package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试流跑流共用核心。
 * <p>
 * 负责：解析目标参数、跑前落盘未提交改图、触发正式 Run、阻塞等待终态、组装失败摘要回执。
 */
@RequiredArgsConstructor
public class TestFlowRunTriggerCore {

    /** 触发与等待 Run */
    private final ITestFlowExecutionService testFlowExecutionService;
    /** 查询 Run 头 */
    private final ITestFlowRunService testFlowRunService;
    /** 查询失败步骤 */
    private final ITestFlowRunStepService testFlowRunStepService;
    /** 落盘测试流画布 */
    private final ITestFlowService testFlowService;
    /** 落盘前校验画布结构 */
    private final GraphJsonValidator graphJsonValidator;
    /** 落盘前规范化设计期检查项 */
    private final FlowDesignPatchNormalizer patchNormalizer;

    /**
     * 从工具入参与上下文解析要跑的流、场景、环境。
     * 流 id：优先参数，其次上下文中的测试流 id。
     * 场景/环境：优先参数；开启画布默认回退时再用上下文默认值。
     *
     * @param arguments         工具业务参数
     * @param ctx               请求上下文，可空
     * @param useCanvasDefaults 是否允许用上下文默认场景/环境补全
     * @return 解析结果（字段均可空）
     */
    public static ResolvedRunArgs resolveArgs(Map<String, Object> arguments,
                                              FlowDesignToolContext ctx,
                                              boolean useCanvasDefaults) {
        Long testFlowId = FlowDesignToolSupport.longArg(
                arguments != null ? arguments.get("testFlowId") : null);
        if (testFlowId == null && ctx != null) {
            testFlowId = ctx.getTestFlowId();
        }
        String runScenarioId = FlowDesignToolSupport.stringArg(
                arguments != null ? arguments.get("runScenarioId") : null);
        if (runScenarioId != null && runScenarioId.isBlank()) {
            runScenarioId = null;
        }
        if (runScenarioId == null && useCanvasDefaults && ctx != null) {
            runScenarioId = ctx.getDefaultRunScenarioId();
        }
        Long envId = FlowDesignToolSupport.longArg(
                arguments != null ? arguments.get("testProjectEnvId") : null);
        if (envId == null && useCanvasDefaults && ctx != null) {
            envId = ctx.getDefaultTestProjectEnvId();
        }
        return new ResolvedRunArgs(testFlowId, runScenarioId, envId);
    }

    /**
     * 跑流目标解析结果。
     *
     * @param testFlowId       测试流 id
     * @param runScenarioId    运行场景 id，可空
     * @param testProjectEnvId 环境 id，可空
     */
    public record ResolvedRunArgs(Long testFlowId, String runScenarioId, Long testProjectEnvId) {
    }

    /**
     * 执行一次全自动跑流并返回工具回执 JSON。
     * 半自动、模板流、未确认提案会直接返回错误；成功则含 runId、status、失败摘要等。
     *
     * @param testFlowId       要跑的测试流 id
     * @param runScenarioId    可选场景 id
     * @param testProjectEnvId 可选环境 id
     * @param operatorUserId   操作者用户 id；可空则由触发服务取当前登录用户
     * @param ctx              工具上下文（落盘、回调、结果字节上限）
     * @return 工具回执 JSON 字符串
     */
    public String run(Long testFlowId,
                      String runScenarioId,
                      Long testProjectEnvId,
                      Long operatorUserId,
                      FlowDesignToolContext ctx) {
        if (testFlowId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testFlowId");
        }
        if (ctx == null || !ctx.isAutopilotEnabled()) {
            return FlowDesignToolSupport.errorJson("当前为半自动，不能调用 run_test_flow；请用户切换到全自动");
        }
        if (ctx.isTemplateDesignMode()) {
            return FlowDesignToolSupport.errorJson("模板预制流不支持 run_test_flow");
        }
        String pendingUpsert = ctx.pendingUpsertBlockReason();
        if (pendingUpsert != null) {
            return FlowDesignToolSupport.errorJson(pendingUpsert + "，请先让用户确认后再 run");
        }

        // 有未落盘的已接受改图单元时，先写库再跑库中最新图
        boolean autoCommitted = false;
        if (ctx.getSubmitCapture() != null && ctx.getSubmitCapture().hasAccepted()) {
            FlowDesignAutopilotCommitSupport.CommitOutcome commit =
                    FlowDesignAutopilotCommitSupport.commitIfNeeded(
                            ctx, testFlowService, graphJsonValidator, patchNormalizer);
            if (!commit.ok()) {
                JSONObject fail = commit.toJson();
                fail.put("error", "跑流前自动落盘失败: " + commit.message());
                fail.put("hint", "请根据 errors 用 submit_* 修正后再 run_test_flow");
                return fail.toJSONString();
            }
            autoCommitted = commit.committed();
        }

        Long runId;
        try {
            FlowExternalChangeSourceHolder.set(
                    FlowExternalChangeSourceHolder.mcpOrWebAutopilot(ctx.getAiChatSessionId() != null));
            TriggerTestFlowRunParams params = TriggerTestFlowRunParams.builder()
                    .testFlowId(testFlowId)
                    .runScenarioId(runScenarioId)
                    .testProjectEnvId(testProjectEnvId)
                    .triggerType("ai")
                    .operatorUserId(operatorUserId)
                    .build();
            runId = testFlowExecutionService.triggerRun(params);
        } catch (FlowExecutionException e) {
            JSONObject fail = new JSONObject();
            fail.put("ok", false);
            fail.put("error", e.getMessage() != null ? e.getMessage() : "运行失败");
            if (e.getErrorCode() != null) {
                fail.put("errorCode", e.getErrorCode().name());
            }
            fail.put("autoCommittedBeforeRun", autoCommitted);
            fail.put("hint", "若为就绪/鉴权硬拦，请 submit_* 修复后再 run_test_flow；最多再修 2 轮");
            return fail.toJSONString();
        } catch (ServiceException e) {
            return FlowDesignToolSupport.errorJson(e.getMessage());
        } catch (Exception e) {
            return FlowDesignToolSupport.errorJson("触发 Run 异常: " + e.getMessage());
        } finally {
            FlowExternalChangeSourceHolder.clear();
        }

        // 通知画布开始按步骤高亮，再阻塞等待终态
        ctx.notifyRunStarted(runId);
        TestFlowRunResult run = testFlowExecutionService.awaitRunTerminal(runId, 600_000L);
        if (run == null) {
            run = testFlowRunService.selectTestFlowRunResult(runId);
        }
        String status = run != null ? run.getStatus() : null;
        boolean failed = RunStatus.FAILED.equals(status);
        boolean paused = RunStatus.PAUSED.equals(status);
        boolean passed = RunStatus.PASSED.equals(status);
        boolean stillRunning = RunStatus.RUNNING.equals(status);

        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("runId", String.valueOf(runId));
        result.put("status", status);
        result.put("failed", failed);
        result.put("paused", paused);
        result.put("passed", passed);
        result.put("autoCommittedBeforeRun", autoCommitted);
        if (stillRunning) {
            result.put("hint", "Run 仍在执行中（等待超时），请提示用户查看运行库或稍后 get_run_failure");
            return ToolResultByteFit.fitAck(result, ctx.getMaxToolResultBytes());
        }

        if (failed) {
            GetRunFailureTool failureTool = new GetRunFailureTool(testFlowRunService, testFlowRunStepService);
            Map<String, Object> failArgs = new HashMap<>();
            failArgs.put("runId", String.valueOf(runId));
            String failureJson = failureTool.execute(failArgs, ctx);
            try {
                JSONObject failure = JSON.parseObject(failureJson);
                if (failure != null && !failure.containsKey("error")) {
                    result.put("failures", failure.get("failures"));
                    result.put("failureCount", failure.get("failureCount"));
                    result.put("bizCodeFailureCount", failure.get("bizCodeFailureCount"));
                    result.put("assertFailureCount", failure.get("assertFailureCount"));
                    result.put("otherFailureCount", failure.get("otherFailureCount"));
                } else if (failure != null && failure.getString("error") != null) {
                    result.put("failureLookupError", failure.getString("error"));
                }
            } catch (Exception ignored) {
                result.put("failureRaw", failureJson);
            }
            result.put("hint", "Run 失败：请根据 failures 用 submit_* 修复，再 run_test_flow（会自动落盘）；失败后再修最多 2 轮");
        } else if (paused) {
            result.put("hint", "Run 暂停（await-input），全自动应停止，等待用户处理");
        } else if (passed) {
            result.put("hint", "Run 已通过，全自动结束");
        } else {
            result.put("hint", "Run 已结束，status=" + status);
        }
        return ToolResultByteFit.fitAck(result, ctx.getMaxToolResultBytes());
    }
}
