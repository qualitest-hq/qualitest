package com.qualitest.flow.openhttp;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.input.EntryInputShapeValidator;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.context.FlowRunContextBuilder;
import com.qualitest.flow.delivery.DeferredResultDeliverySink;
import com.qualitest.flow.delivery.EntryKind;
import com.qualitest.flow.delivery.InboundSlotRegistry;
import com.qualitest.flow.delivery.OutPayload;
import com.qualitest.flow.delivery.RunDelivery;
import com.qualitest.flow.delivery.RunTrigger;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.run.FlowGraphRunner;
import com.qualitest.flow.validate.FlowRunReadinessGate;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 开放 HTTP 握连接入站服务。
 * <p>
 * 处理流程：
 * <ol>
 *   <li>按项目 + method + path 查路由；未登记立即 404</li>
 *   <li>若入口声明了 inputShape，校验 query / headers / body 的必填与类型；不符立即 400，不跑图</li>
 *   <li>跑前就绪闸：图结构 / 鉴权等非法则 400，不跑图</li>
 *   <li>占入站名额；满员立即 503，不跑图</li>
 *   <li>把 query / headers / body 灌入运行时 {@code in}，挂 DeliverySink 与名额令牌</li>
 *   <li>后台线程跑图；http-return 或收束时写出并腾槽；Input 暂停则 503</li>
 * </ol>
 * 客户端超时或断开只标记 sink 断开，不取消跑图；写出失败时仍释放名额。
 * 跑图未捕获异常且尚未交付时，用 502 错误包 complete，并释放令牌。
 */
@Service
public class OpenHttpIngressService {

    /** 握连接最长等待毫秒；超时只断写出端，图继续 */
    private static final long TIMEOUT_MS = 120_000L;

    /** 开放路由目录，按 method+path 查找路由 */
    private final OpenHttpRouteCatalog routeCatalog;
    /** 入站名额登记表 */
    private final InboundSlotRegistry inboundSlotRegistry;
    /** 跑图引擎 */
    private final FlowGraphRunner flowGraphRunner;
    /** 节点执行器注册表 */
    private final NodeHandlerRegistry nodeHandlerRegistry;
    /** 正式开跑前就绪闸 */
    private final FlowRunReadinessGate flowRunReadinessGate;
    /** 测试环境服务 */
    private final ITestProjectEnvService testProjectEnvService;
    /** 测试项目数据访问 */
    private final TestProjectMapper testProjectMapper;
    /** 后台跑图线程池 */
    private final ExecutorService runPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "open-http-ingress");
        t.setDaemon(true);
        return t;
    });

    /** 组装路由目录、跑图引擎、就绪闸与环境服务，供开放入站异步执行 */
    public OpenHttpIngressService(OpenHttpRouteCatalog routeCatalog,
                                  InboundSlotRegistry inboundSlotRegistry,
                                  FlowGraphRunner flowGraphRunner,
                                  NodeHandlerRegistry nodeHandlerRegistry,
                                  FlowRunReadinessGate flowRunReadinessGate,
                                  ITestProjectEnvService testProjectEnvService,
                                  TestProjectMapper testProjectMapper) {
        this.routeCatalog = routeCatalog;
        this.inboundSlotRegistry = inboundSlotRegistry;
        this.flowGraphRunner = flowGraphRunner;
        this.nodeHandlerRegistry = nodeHandlerRegistry;
        this.flowRunReadinessGate = flowRunReadinessGate;
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

        // 入口声明了 inputShape 时先校验入参形状，不符直接返回 400
        Optional<String> shapeError = validateIngressShape(route, query, headers, body);
        if (shapeError.isPresent()) {
            deferred.setResult(platformError(
                    HttpStatus.BAD_REQUEST,
                    EntryInputShapeValidator.ERROR_CODE,
                    shapeError.get()));
            return deferred;
        }

        GraphJson graph = route.graph();
        List<String> readinessErrors = flowRunReadinessGate.collectBlockingErrors(graph, testProjectId);
        if (readinessErrors != null && !readinessErrors.isEmpty()) {
            deferred.setResult(platformError(
                    HttpStatus.BAD_REQUEST,
                    "OPEN_GRAPH_NOT_READY",
                    readinessErrors.get(0)));
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
        ctx.setTrigger(RunTrigger.HTTP.getCode());
        // 入口绑定了环境时，把环境 id 写入上下文供插件步骤使用
        if (route.envId() != null) {
            ctx.setTestProjectEnvId(route.envId());
        }
        ctx.setDeliverySink(sink);
        ctx.setInboundSlotToken(slot);
        if (project != null) {
            ctx.setProjectAuthConfig(project.getAuthConfig());
        }

        runPool.execute(() -> {
            try {
                FlowGraphRunner.Outcome outcome = flowGraphRunner.run(graph, ctx, nodeHandlerRegistry);
                // Input 等人机暂停：开放入站无法续跑，对调用方返回 503
                if (outcome != null && outcome.isPaused() && !ctx.isDelivered()) {
                    OutPayload err = new OutPayload();
                    err.setStatus(503);
                    err.getHeaders().put("Content-Type", RunDelivery.DEFAULT_CONTENT_TYPE);
                    err.setBody(Map.of(
                            "code", "OPEN_AWAIT_INPUT",
                            "message", "开放入站遇到人工输入，暂不可自动完成"));
                    sink.complete(err);
                    inboundSlotRegistry.release(slot);
                    ctx.setInboundSlotToken(null);
                    ctx.setDelivered(true);
                }
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

    /**
     * 按入口节点 data.inputShape 校验入站请求；找不到入口节点或未声明 shape 时不拦截。
     *
     * @param route   命中的开放路由
     * @param query   查询参数
     * @param headers 请求头
     * @param body    请求体
     * @return 校验失败原因；通过或无需校验时为 empty
     */
    private static Optional<String> validateIngressShape(
            OpenHttpRoute route,
            Map<String, Object> query,
            Map<String, String> headers,
            Object body) {
        if (route == null || route.graph() == null || route.graph().getNodes() == null) {
            return Optional.empty();
        }
        GraphNode entry = null;
        for (GraphNode n : route.graph().getNodes()) {
            if (n != null && route.entryNodeId().equals(n.getId())) {
                entry = n;
                break;
            }
        }
        if (entry == null || entry.getData() == null) {
            return Optional.empty();
        }
        Object rawShape = entry.getData().get("inputShape");
        if (!(rawShape instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> inputShape = (Map<String, Object>) map;
        return EntryInputShapeValidator.validate(inputShape, query, headers, body);
    }

    /** 平台侧即时错误（未开跑）：统一 JSON 形态 code + message */
    private static ResponseEntity<Object> platformError(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(RunDelivery.DEFAULT_CONTENT_TYPE))
                .body(Map.of("code", code, "message", message));
    }
}
