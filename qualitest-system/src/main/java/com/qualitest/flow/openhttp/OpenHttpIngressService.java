package com.qualitest.flow.openhttp;

import cn.hutool.core.util.IdUtil;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.context.EntryRunPolicySupport;
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
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.FlowGraphRunner;
import com.qualitest.flow.run.FlowNodePreExecuteHook;
import com.qualitest.flow.run.RunContinuation;
import com.qualitest.flow.run.RunPersistenceService;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.run.RunStatusUpdater;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
import com.qualitest.flow.validate.FlowGraphValidateService;
import com.qualitest.flow.validate.FlowIssue;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestProjectEnvService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 开放 HTTP 握连接入站服务。
 * <p>
 * 处理流程：
 * <ol>
 *   <li>按项目 + method + path 查路由；未登记立即 404</li>
 *   <li>若入口声明了 inputShape，校验 query / headers / body 的必填与类型；不符立即 400，不跑图</li>
 *   <li>跑前就绪闸：图结构 / 鉴权等非法则 400，不跑图</li>
 *   <li>占入站名额；满员立即 503，不跑图</li>
 *   <li>落一条 trigger=http 的运行记录，把 query / headers / body 灌入 {@code in}</li>
 *   <li>后台线程跑图并逐步落库；http-return 或收束时写出并腾槽；Input 暂停则 503</li>
 * </ol>
 * 客户端超时或断开只标记 sink 断开，不取消跑图；写出失败时仍释放名额。
 * 跑图未捕获异常且尚未交付时，用 502 错误包 complete，并释放令牌。
 */
@Service
public class OpenHttpIngressService {

    /** 握连接最长等待毫秒；超时只断写出端，图继续 */
    private static final long TIMEOUT_MS = 120_000L;

    private final OpenHttpRouteRegistry routeRegistry;
    private final InboundSlotRegistry inboundSlotRegistry;
    private final FlowGraphRunner flowGraphRunner;
    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final FlowGraphValidateService flowGraphValidateService;
    private final ITestProjectEnvService testProjectEnvService;
    private final TestProjectMapper testProjectMapper;
    private final ITestFlowRunService testFlowRunService;
    private final RunPersistenceService runPersistenceService;
    private final StepResultWriter stepResultWriter;
    private final RunStatusUpdater runStatusUpdater;
    private final ExecutorService runPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "open-http-ingress");
        t.setDaemon(true);
        return t;
    });

    public OpenHttpIngressService(OpenHttpRouteRegistry routeRegistry,
                                  InboundSlotRegistry inboundSlotRegistry,
                                  FlowGraphRunner flowGraphRunner,
                                  NodeHandlerRegistry nodeHandlerRegistry,
                                  FlowGraphValidateService flowGraphValidateService,
                                  ITestProjectEnvService testProjectEnvService,
                                  TestProjectMapper testProjectMapper,
                                  ITestFlowRunService testFlowRunService,
                                  RunPersistenceService runPersistenceService,
                                  StepResultWriter stepResultWriter,
                                  RunStatusUpdater runStatusUpdater) {
        this.routeRegistry = routeRegistry;
        this.inboundSlotRegistry = inboundSlotRegistry;
        this.flowGraphRunner = flowGraphRunner;
        this.nodeHandlerRegistry = nodeHandlerRegistry;
        this.flowGraphValidateService = flowGraphValidateService;
        this.testProjectEnvService = testProjectEnvService;
        this.testProjectMapper = testProjectMapper;
        this.testFlowRunService = testFlowRunService;
        this.runPersistenceService = runPersistenceService;
        this.stepResultWriter = stepResultWriter;
        this.runStatusUpdater = runStatusUpdater;
    }

    /**
     * 受理一次开放入站，立即返回 DeferredResult；实际响应在 return/收束时写入。
     * <p>
     * 占到入站名额之后、线程池接手之前的准备失败（环境/素材查询、Run 落库等）
     * 一律释放名额并返回 502 平台错包，避免名额泄漏把后续入站全部挤成 503。
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
        OpenHttpRoute route = routeRegistry.resolve(testProjectId, method, path);
        if (route == null) {
            deferred.setResult(platformError(HttpStatus.NOT_FOUND, "OPEN_PATH_NOT_FOUND", "未登记的开放 path"));
            return deferred;
        }

        Optional<String> shapeError = validateIngressShape(route, query, headers, body);
        if (shapeError.isPresent()) {
            deferred.setResult(platformError(
                    HttpStatus.BAD_REQUEST,
                    EntryInputShapeValidator.ERROR_CODE,
                    shapeError.get()));
            return deferred;
        }

        GraphJson graph = route.graph();
        List<String> readinessErrors = flowGraphValidateService.collectBlockingErrorMessages(
                graph,
                FlowIssue.Stage.INGRESS,
                testProjectId,
                route.testFlowId(),
                null);
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
        deferred.onTimeout(sink::markDisconnected);
        deferred.onError(t -> sink.markDisconnected());

        Date startedAt = DateUtils.getNowDate();
        FlowRunContext ctx;
        Long runId;
        try {
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

            runId = IdUtil.getSnowflakeNextId();
            ctx = FlowRunContextBuilder.build(env, assetJson, null, testProjectId, true);
            ctx.setIn(in);
            ctx.setEntryKind(EntryKind.HTTP.getCode());
            ctx.setEntryNodeId(route.entryNodeId());
            ctx.setTrigger(RunTrigger.HTTP.getCode());
            if (route.envId() != null) {
                ctx.setTestProjectEnvId(route.envId());
            }
            ctx.setTestFlowRunId(runId);
            ctx.setDeliverySink(sink);
            ctx.setInboundSlotToken(slot);
            if (project != null) {
                ctx.setProjectAuthConfig(project.getAuthConfig());
            }

            // 落库放最后：此前的准备失败不会留下 RUNNING 幽灵行
            String snapshotJson = graph.toJsonString();
            TestFlowRun run = TestFlowRun.builder()
                    .testFlowRunId(runId)
                    .testFlowId(route.testFlowId())
                    .testProjectEnvId(route.envId())
                    .status(RunStatus.RUNNING.getCode())
                    .graphJsonSnapshot(snapshotJson)
                    .graphFingerprint(StepResultWriter.fingerprint(snapshotJson))
                    .startedAt(startedAt)
                    .triggerType(RunTrigger.HTTP.getCode())
                    .delStatus(0)
                    .build();
            run.setCreateTime(startedAt);
            testFlowRunService.insertTestFlowRun(run);
        } catch (Exception e) {
            // 占位之后、开跑之前的任何失败都须交回名额，否则会逐步打满入站上限
            inboundSlotRegistry.release(slot);
            deferred.setResult(platformError(
                    HttpStatus.BAD_GATEWAY,
                    "OPEN_RUN_ERROR",
                    e.getMessage() != null ? e.getMessage() : "开放入站准备失败"));
            return deferred;
        }

        try {
            runPool.execute(() -> {
                long runT0 = System.currentTimeMillis();
                AtomicLong stepIndex = new AtomicLong(1L);
                try {
                    RunSnapshotPolicy policy = EntryRunPolicySupport.compose(
                            graph, route.entryNodeId(), null);
                    FlowGraphRunner.Outcome outcome = flowGraphRunner.run(
                            graph, ctx, nodeHandlerRegistry,
                            FlowNodePreExecuteHook.NONE, RunContinuation.fresh(), policy,
                            result -> persistStep(runId, stepIndex.getAndIncrement(), result));

                    if (outcome != null && outcome.isPaused() && !ctx.isDelivered()) {
                        completeUndelivered(sink, ctx, slot, 503, "OPEN_AWAIT_INPUT",
                                "开放入站遇到人工输入，暂不可自动完成");
                        finishRun(runId, startedAt, runT0, ctx, false,
                                FlowErrorCode.TF_AWAIT_INPUT.getCode(), "开放入站遇到人工输入");
                        return;
                    }
                    if (outcome != null && outcome.isPaused()) {
                        // 响应已交付：连接已写完，无法再暂停等待人工输入；明确记失败并留可读原因
                        finishRun(runId, startedAt, runT0, ctx, false,
                                FlowErrorCode.TF_AWAIT_INPUT.getCode(),
                                "响应已交付后遇到人工输入，开放入站不再续跑");
                        return;
                    }

                    boolean passed = outcome != null && outcome.isPassed();
                    String errorCode = null;
                    String errorMessage = null;
                    if (!passed && outcome != null && outcome.getError() != null) {
                        errorCode = outcome.getError().getCode();
                        errorMessage = outcome.getError().getMessage();
                    }
                    finishRun(runId, startedAt, runT0, ctx, passed, errorCode, errorMessage);
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "开放入站跑图异常";
                    if (!ctx.isDelivered()) {
                        completeUndelivered(sink, ctx, slot, 502, "OPEN_RUN_ERROR", msg);
                    }
                    finishRun(runId, startedAt, runT0, ctx, false, FlowErrorCode.TF_STEP_ERROR.getCode(), msg);
                } finally {
                    releaseInboundSlotIfHeld(ctx);
                }
            });
        } catch (Exception e) {
            // 线程池拒绝：Run 行已落库，收尾为失败并交回名额
            String msg = e.getMessage() != null ? e.getMessage() : "开放入站提交执行失败";
            releaseInboundSlotIfHeld(ctx);
            finishRun(runId, startedAt, System.currentTimeMillis(), ctx, false,
                    FlowErrorCode.TF_STEP_ERROR.getCode(), msg);
            deferred.setResult(platformError(HttpStatus.BAD_GATEWAY, "OPEN_RUN_ERROR", msg));
        }
        return deferred;
    }

    /**
     * 尚未交付时写出平台错包并腾入站名额。
     * 仅用于 Input 暂停 / 未捕获异常等「握连接须立刻有响应」的路径。
     */
    private void completeUndelivered(
            DeferredResultDeliverySink sink,
            FlowRunContext ctx,
            String slot,
            int status,
            String code,
            String message) {
        OutPayload err = new OutPayload();
        err.setStatus(status);
        err.getHeaders().put("Content-Type", RunDelivery.DEFAULT_CONTENT_TYPE);
        err.setBody(Map.of("code", code, "message", message));
        sink.complete(err);
        inboundSlotRegistry.release(slot);
        ctx.setInboundSlotToken(null);
        ctx.setDelivered(true);
    }

    /** 若上下文仍持有入站令牌则释放（finally 兜底） */
    private void releaseInboundSlotIfHeld(FlowRunContext ctx) {
        if (ctx.getInboundSlotToken() != null) {
            inboundSlotRegistry.release(ctx.getInboundSlotToken());
            ctx.setInboundSlotToken(null);
        }
    }

    /** 逐步写入运行步骤；落库失败不中断跑图 */
    private void persistStep(Long testFlowRunId, long stepIndex, StepResult result) {
        try {
            runPersistenceService.insertStep(stepResultWriter.toEntity(testFlowRunId, (int) stepIndex, result));
        } catch (Exception ignored) {
            // 步骤落库失败不影响交付
        }
    }

    /** 标记开放入站 Run 终态；未占环境槽时生命周期释放为无操作 */
    private void finishRun(Long testFlowRunId, Date startedAt, long runT0, FlowRunContext ctx,
                           boolean passed, String errorCode, String errorMessage) {
        try {
            runStatusUpdater.markFinished(
                    testFlowRunId,
                    passed ? RunStatus.PASSED.getCode() : RunStatus.FAILED.getCode(),
                    startedAt,
                    runT0,
                    ctx,
                    errorCode,
                    errorMessage);
        } catch (Exception ignored) {
            // 终态落库失败不影响已交付响应
        }
    }

    /**
     * 按入口节点 data.inputShape 校验入站请求；找不到入口节点或未声明 shape 时不拦截。
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
