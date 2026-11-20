package com.qualitest.flow.inbound;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.FlowRunContextBuilder;
import com.qualitest.flow.context.ResolvedRunScenario;
import com.qualitest.flow.context.RunScenarioBootstrap;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphInputField;
import com.qualitest.flow.model.GraphInputSpec;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.run.ExecutionOutcome;
import com.qualitest.flow.run.RunBootstrapMeta;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.run.RunStatusUpdater;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.flow.run.TestFlowExecutor;
import com.qualitest.flow.validate.FlowRunReadinessGate;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.runhub.EnvRunConcurrencyService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectEnvService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放入站执行：按完整 path 找流，用契约绑定的运行环境跑图，把请求灌入 {@code in}，再把 {@code out} 刷成 HTTP 回包。
 * <p>
 * 响应体就是出参内容，外层不加平台信封，也不返回 runId。
 * 匿名调用不允许外联 HTTP；项目按环境串行时先抢环境槽，抢不到返回 503，不会排队。
 * 跑到人工输入暂停时直接中止并释槽。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenInboundExecutionService {

    private final OpenInboundPathIndex pathIndex;
    private final ITestFlowService testFlowService;
    private final ITestProjectEnvService testProjectEnvService;
    private final TestProjectMapper testProjectMapper;
    private final ITestFlowRunService testFlowRunService;
    private final TestFlowExecutor testFlowExecutor;
    private final FlowRunReadinessGate flowRunReadinessGate;
    private final RunStatusUpdater runStatusUpdater;
    private final EnvRunConcurrencyService envRunConcurrencyService;

    /**
     * 按开放完整 path 查找测试流，校验就绪与绑定环境，注入入参后同步跑图，返回 flush 结果。
     *
     * @param path        完整相对 path（如 {@code /v3/refund}）
     * @param requestBody 请求体原文，可空
     * @param contentType Content-Type，可空
     */
    public OpenInboundOutFlush.FlushResult handle(String path, String requestBody, String contentType) {
        Long flowId = pathIndex.findFlowId(path);
        if (flowId == null) {
            return OpenInboundOutFlush.FlushResult.error(404, "未找到开放入站 path: " + path);
        }
        TestFlow testFlow = testFlowService.selectTestFlowById(flowId);
        if (testFlow == null || (testFlow.getDelStatus() != null && testFlow.getDelStatus() != 0)) {
            return OpenInboundOutFlush.FlushResult.error(404, "开放入站对应测试流不存在");
        }
        if (StrUtil.isBlank(testFlow.getGraphJson())) {
            return OpenInboundOutFlush.FlushResult.error(502, "测试流 graph_json 为空");
        }

        GraphJson graph;
        try {
            graph = GraphJson.parse(testFlow.getGraphJson());
        } catch (Exception e) {
            return OpenInboundOutFlush.FlushResult.error(502, "图解析失败: " + e.getMessage());
        }
        GraphMeta meta = graph.getMeta();
        GraphInputSpec spec = meta != null ? meta.getInputSpec() : null;
        if (spec == null || !Boolean.TRUE.equals(spec.getOpen())) {
            return OpenInboundOutFlush.FlushResult.error(404, "该测试流未开放入站");
        }
        if (StrUtil.isBlank(spec.getRunEnvId())) {
            return OpenInboundOutFlush.FlushResult.error(502, "开放入站未绑定运行环境");
        }
        Long runEnvId;
        try {
            runEnvId = Long.parseLong(spec.getRunEnvId().trim());
        } catch (NumberFormatException e) {
            return OpenInboundOutFlush.FlushResult.error(502, "开放入站运行环境 ID 无效");
        }

        List<String> readinessErrors =
                flowRunReadinessGate.collectBlockingErrors(graph, testFlow.getTestProjectId());
        if (!readinessErrors.isEmpty()) {
            return OpenInboundOutFlush.FlushResult.error(502, String.join("; ", readinessErrors));
        }

        TestProjectEnv envQuery = new TestProjectEnv();
        envQuery.setTestProjectId(testFlow.getTestProjectId());
        envQuery.setDelStatus(0);
        List<TestProjectEnv> projectEnvs = testProjectEnvService.selectTestProjectEnvList(envQuery);
        List<RunScenarioBootstrap.EnvHint> hints = new ArrayList<>();
        if (projectEnvs != null) {
            for (TestProjectEnv e : projectEnvs) {
                if (e != null) {
                    hints.add(new RunScenarioBootstrap.EnvHint(e.getTestProjectEnvId(), e.getEnvName()));
                }
            }
        }

        ResolvedRunScenario scenario;
        try {
            // 环境强制用契约 runEnvId；场景仅提供 flow/in 缺省等
            scenario = RunScenarioBootstrap.resolve(
                    graph,
                    null,
                    runEnvId,
                    null,
                    RunScenarioBootstrap.formatAvailableEnvHint(hints),
                    null
            );
        } catch (FlowExecutionException e) {
            return OpenInboundOutFlush.FlushResult.error(502, e.getMessage());
        }

        TestProjectEnv env = testProjectEnvService.selectTestProjectEnvById(scenario.getTestProjectEnvId());
        if (env == null || (env.getDelStatus() != null && env.getDelStatus() != 0)) {
            return OpenInboundOutFlush.FlushResult.error(502, "测试环境不存在");
        }

        TestProject project = testProjectMapper.selectTestProjectById(testFlow.getTestProjectId());
        // 匿名开放入站不允许外联 HTTP
        String assetJson = testProjectMapper.selectAssetVariablesByTestProjectId(testFlow.getTestProjectId());
        FlowRunContext ctx = FlowRunContextBuilder.build(
                env, assetJson, scenario.getInSeed(),
                testFlow.getTestProjectId(), false);
        if (project != null) {
            ctx.setProjectAuthConfig(project.getAuthConfig());
        }

        injectRequestIntoIn(ctx, spec, requestBody, contentType);
        String missingRequired = missingRequiredFields(ctx, spec);
        if (missingRequired != null) {
            return OpenInboundOutFlush.FlushResult.error(400, missingRequired);
        }

        Long runId = IdUtil.getSnowflakeNextId();
        boolean serialByEnv = project != null
                && envRunConcurrencyService.isSerialByEnv(project.getRunConcurrencyMode());
        boolean slotHeld = false;
        if (serialByEnv) {
            if (!envRunConcurrencyService.tryAcquireEnvSlot(env.getTestProjectEnvId(), runId)) {
                return OpenInboundOutFlush.FlushResult.error(503, "环境占用中，请稍后重试");
            }
            slotHeld = true;
        }

        String snapshotJson = graph.toJsonString();
        Date now = DateUtils.getNowDate();
        TestFlowRun run = TestFlowRun.builder()
                .testFlowRunId(runId)
                .testFlowId(testFlow.getTestFlowId())
                .testProjectEnvId(scenario.getTestProjectEnvId())
                .runScenarioId(scenario.getScenarioId())
                .status(RunStatus.RUNNING.getCode())
                .graphJsonSnapshot(snapshotJson)
                .graphFingerprint(StepResultWriter.fingerprint(snapshotJson))
                .startedAt(now)
                .triggerType("open_inbound")
                .delStatus(0)
                .build();
        run.setCreateTime(now);
        try {
            testFlowRunService.insertTestFlowRun(run);
        } catch (Exception e) {
            if (slotHeld) {
                envRunConcurrencyService.releaseEnvSlot(env.getTestProjectEnvId(), runId);
            }
            return OpenInboundOutFlush.FlushResult.error(502, "创建运行记录失败: " + e.getMessage());
        }

        if (slotHeld) {
            envRunConcurrencyService.markRunStarted(
                    testFlow.getTestProjectId(),
                    env.getTestProjectEnvId(),
                    env.getEnvName(),
                    testFlow.getTestFlowId(),
                    testFlow.getFlowName(),
                    runId,
                    scenario.getScenarioId(),
                    "open_inbound",
                    null);
        }

        RunBootstrapMeta bootstrap = new RunBootstrapMeta(scenario, env.getEnvName(), env);
        ExecutionOutcome outcome;
        try {
            outcome = testFlowExecutor.execute(runId, graph, ctx, bootstrap);
        } catch (Exception e) {
            log.warn("开放入站跑图异常 path={} runId={}: {}", path, runId, e.getMessage());
            try {
                runStatusUpdater.markFinished(runId, RunStatus.FAILED.getCode(), now,
                        now.getTime(), ctx, FlowErrorCode.TF_STEP_ERROR.getCode(),
                        e.getMessage() != null ? e.getMessage() : "运行异常");
            } catch (Exception ignored) {
                if (slotHeld) {
                    envRunConcurrencyService.releaseEnvSlot(env.getTestProjectEnvId(), runId);
                }
            }
            return OpenInboundOutFlush.FlushResult.error(502, "跑图异常: " + e.getMessage());
        }

        if (outcome != null && outcome.isPaused()) {
            // 暂停态不会自动释槽：这里中止 Run 以释放环境占用
            try {
                runStatusUpdater.markAborted(runId, now, System.currentTimeMillis() - now.getTime(),
                        FlowErrorCode.TF_AWAIT_INPUT.getCode(), "开放入站不支持暂停等待人工输入");
            } catch (Exception e) {
                if (slotHeld) {
                    envRunConcurrencyService.releaseEnvSlot(env.getTestProjectEnvId(), runId);
                }
            }
            return OpenInboundOutFlush.FlushResult.error(502, "开放入站不支持暂停等待人工输入");
        }
        if (outcome != null && !outcome.isPassed()) {
            String msg = outcome.getErrorMessage() != null ? outcome.getErrorMessage() : "跑图未通过";
            OpenInboundOutFlush.FlushResult flush = OpenInboundOutFlush.fromOut(ctx.getOut());
            if (flush.ok()) {
                return flush;
            }
            return OpenInboundOutFlush.FlushResult.error(502, msg);
        }

        return OpenInboundOutFlush.fromOut(ctx.getOut());
    }

    /**
     * 检查入参定义里标记为必填的字段在 {@code in} 中是否已有非空值。
     *
     * @return 缺失说明文案；全部满足则 null
     */
    static String missingRequiredFields(FlowRunContext ctx, GraphInputSpec spec) {
        if (spec == null || spec.getFields() == null || spec.getFields().isEmpty()) {
            return null;
        }
        Map<String, Object> in = ctx.getIn() != null ? ctx.getIn() : Map.of();
        List<String> missing = new ArrayList<>();
        for (GraphInputField field : spec.getFields()) {
            if (field == null || field.getName() == null || field.getName().isBlank()) {
                continue;
            }
            if (!Boolean.TRUE.equals(field.getRequired())) {
                continue;
            }
            Object val = in.get(field.getName());
            if (val == null || (val instanceof String s && s.isBlank())) {
                missing.add(field.getName());
            }
        }
        if (missing.isEmpty()) {
            return null;
        }
        return "缺少必填入参: " + String.join(", ", missing);
    }

    /**
     * 把 HTTP 请求灌进 {@code in}。
     * 先补字段缺省值；请求原文写入 {@code _requestBody}；
     * JSON 对象按入参字段名覆盖对应键，无字段声明时则把对象平铺进 {@code in}；
     * 解析后的对象另存 {@code _requestJson}。
     */
    static void injectRequestIntoIn(FlowRunContext ctx, GraphInputSpec spec,
                                    String requestBody, String contentType) {
        if (ctx.getIn() == null) {
            ctx.setIn(new HashMap<>());
        }
        Map<String, Object> in = ctx.getIn();
        if (requestBody != null && !requestBody.isBlank()) {
            in.put("_requestBody", requestBody);
        }
        if (spec.getFields() != null) {
            for (GraphInputField field : spec.getFields()) {
                if (field == null || field.getName() == null || field.getName().isBlank()) {
                    continue;
                }
                if (!in.containsKey(field.getName()) && field.getDefaultValue() != null) {
                    in.put(field.getName(), field.getDefaultValue());
                }
            }
        }
        if (requestBody == null || requestBody.isBlank()) {
            return;
        }
        String ct = contentType != null ? contentType.toLowerCase() : "";
        if (!ct.contains("json") && !requestBody.trim().startsWith("{") && !requestBody.trim().startsWith("[")) {
            return;
        }
        try {
            Object parsed = JSON.parse(requestBody);
            if (parsed instanceof JSONObject obj) {
                Map<String, Object> bodyMap = new LinkedHashMap<>(obj);
                if (spec.getFields() != null && !spec.getFields().isEmpty()) {
                    for (GraphInputField field : spec.getFields()) {
                        if (field == null || field.getName() == null || field.getName().isBlank()) {
                            continue;
                        }
                        if (bodyMap.containsKey(field.getName())) {
                            in.put(field.getName(), bodyMap.get(field.getName()));
                        }
                    }
                } else {
                    for (Map.Entry<String, Object> e : bodyMap.entrySet()) {
                        if (e.getKey() != null && !e.getKey().startsWith("_")) {
                            in.put(e.getKey(), e.getValue());
                        }
                    }
                }
                in.put("_requestJson", bodyMap);
            } else {
                in.put("_requestJson", parsed);
            }
        } catch (Exception e) {
            log.debug("开放入站请求体非 JSON，仅保留原文: {}", e.getMessage());
        }
    }
}
