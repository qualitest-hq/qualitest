package com.qualitest.flow.openhttp;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.FlowRunContextBuilder;
import com.qualitest.flow.delivery.DeferredResultDeliverySink;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.InboundSlotRegistry;
import com.qualitest.flow.delivery.OutPayload;
import com.qualitest.flow.delivery.RunDelivery;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.run.FlowGraphRunner;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestProjectEnvService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 开放 HTTP 握连接入站服务。
 * <p>
 * 处理流程：
 * <ol>
 *   <li>按项目 + method + path 查路由；未登记立即 404</li>
 *   <li>占入站名额；满员立即 503，不跑图</li>
 *   <li>把 query / headers / body 灌入运行时 {@code in}，挂 DeliverySink 与名额令牌</li>
 *   <li>后台线程跑图；http-return 或收束时写出并腾槽</li>
 * </ol>
 * 客户端超时或断开只标记 sink 断开，不取消跑图；写出失败时仍释放名额。
 * 跑图未捕获异常且尚未交付时，用 502 错误包 complete，并释放令牌。
 */
@Service
public class OpenHttpIngressService {

    /** 握连接最长等待毫秒；超时只断写出端，图继续 */
    private static final long TIMEOUT_MS = 120_000L;

    private final OpenHttpRouteCatalog routeCatalog;
    private final InboundSlotRegistry inboundSlotRegistry;
    private final FlowGraphRunner flowGraphRunner;
    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final ITestProjectEnvService testProjectEnvService;
    private final TestProjectMapper testProjectMapper;
    private final ExecutorService runPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "open-http-ingress");
        t.setDaemon(true);
        return t;
    });

    /** 组装路由目录、跑图引擎与环境服务，供开放入站异步执行 */
    public OpenHttpIngressService(OpenHttpRouteCatalog routeCatalog,
                                  InboundSlotRegistry inboundSlotRegistry,
                                  FlowGraphRunner flowGraphRunner,
                                  NodeHandlerRegistry nodeHandlerRegistry,
                                  ITestProjectEnvService testProjectEnvService,
                                  TestProjectMapper testProjectMapper) {
        this.routeCatalog = routeCatalog;
        this.inboundSlotRegistry = inboundSlotRegistry;
        this.flowGraphRunner = flowGraphRunner;
        this.nodeHandlerRegistry = nodeHandlerRegistry;
        this.testProjectEnvService = testProjectEnvService;
        this.testProjectMapper = testProjectMapper;
    }

    /**
     * 受理一次开放入站，立即返回 DeferredResult；实际响应在 return/收束时写入。
     *
     * @param testProjectId 路径上的项目 id
     * @param method        HTTP 方法
     * @param path          去掉 /open-http/{projectId} 前缀后的业务 path
     * @param query         查询参数
     * @param headers       请求头
     * @param body          请求体（可空）
     */
    public DeferredResult<ResponseEntity<Object>> handle(
            Long testProjectId,
            String method,
            String path,
            Map<String, Object> query,
            Map<String, String> headers,
            Object body) {

        DeferredResult<ResponseEntity<Object>> deferred = new DeferredResult<>(TIMEOUT_MS);
        OpenHttpRoute route = routeCatalog.find(testProjectId, method, path);
        if (route == null) {
            deferred.setResult(platformError(HttpStatus.NOT_FOUND, "OPEN_PATH_NOT_FOUND", "未登记的开放 path"));
            return deferred;
        }

        String slot = inboundSlotRegistry.tryAcquire();
        if (slot == null) {
            deferred.setResult(platformError(HttpStatus.SERVICE_UNAVAILABLE, "OPEN_SLOT_FULL", "开放入站名额已满"));
            return deferred;
        }

        DeferredResultDeliverySink sink = new DeferredResultDeliverySink(deferred);
        // 超时/传输错误只断写出，不 interrupt 跑图线程
        deferred.onTimeout(sink::markDisconnected);
        deferred.onError(t -> sink.markDisconnected());

        TestProjectEnv env = route.envId() != null
                ? testProjectEnvService.selectTestProjectEnvById(route.envId())
                : null;
        String assetJson = testProjectMapper.selectAssetVariablesByTestProjectId(testProjectId);
        TestProject project = testProjectMapper.selectTestProjectById(testProjectId);

        Map<String, Object> in = new LinkedHashMap<>();
        if (query != null) {
            in.put("query", query);
        }
        if (headers != null) {
            in.put("headers", new HashMap<>(headers));
        }
        in.put("body", body);

        FlowRunContext ctx = FlowRunContextBuilder.build(env, assetJson, null, testProjectId, true);
        ctx.setIn(in);
        ctx.setEntryKind(EntryKind.HTTP.getCode());
        ctx.setEntryNodeId(route.entryNodeId());
        ctx.setTrigger(EntryKind.HTTP.getCode());
        ctx.setDeliverySink(sink);
        ctx.setInboundSlotToken(slot);
        if (project != null) {
            ctx.setProjectAuthConfig(project.getAuthConfig());
        }

        GraphJson graph = route.graph();
        runPool.execute(() -> {
            try {
                flowGraphRunner.run(graph, ctx, nodeHandlerRegistry);
            } catch (Exception e) {
                if (!ctx.isDelivered()) {
                    OutPayload err = new OutPayload();
                    err.setStatus(502);
                    err.getHeaders().put("Content-Type", RunDelivery.DEFAULT_CONTENT_TYPE);
                    err.setBody(Map.of("code", "OPEN_RUN_ERROR",
                            "message", e.getMessage() != null ? e.getMessage() : "开放入站跑图异常"));
                    sink.complete(err);
                    inboundSlotRegistry.release(slot);
                    ctx.setInboundSlotToken(null);
                    ctx.setDelivered(true);
                }
            } finally {
                // 极端情况下仍持有令牌则释放，避免名额泄漏
                if (ctx.getInboundSlotToken() != null) {
                    inboundSlotRegistry.release(ctx.getInboundSlotToken());
                    ctx.setInboundSlotToken(null);
                }
            }
        });
        return deferred;
    }

    /** 平台侧即时错误（未开跑）：统一 JSON 形态 code + message */
    private static ResponseEntity<Object> platformError(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(RunDelivery.DEFAULT_CONTENT_TYPE))
                .body(Map.of("code", code, "message", message));
    }
}
