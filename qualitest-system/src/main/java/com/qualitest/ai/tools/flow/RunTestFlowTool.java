package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.run.RunStatus;
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
 * 全自动专用工具：触发当前测试流 Run，返回 runId、status、失败摘要。
 * <p>
 * 仅当上下文 autopilotEnabled=true 时可调用；半自动直接拒绝。
 * 若本轮仍有已接受但未写库的 submit 单元，会先隐式落盘再触发运行。
 * 触发后立刻回调 onRunStarted（画布可开始高亮），工具线程再阻塞等待终态，
 * 把 status / failures 回给模型；失败后再修最多 2 轮。
 */
@RequiredArgsConstructor
public class RunTestFlowTool implements QualitestTool {

    private final ITestFlowExecutionService testFlowExecutionService;
    private final ITestFlowRunService testFlowRunService;
    private final ITestFlowRunStepService testFlowRunStepService;
    private final ITestFlowService testFlowService;
    private final GraphJsonValidator graphJsonValidator;
    private final FlowDesignPatchNormalizer patchNormalizer;

    @Override
    public String getName() {
        return FlowDesignToolNames.RUN_TEST_FLOW.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        // 半自动：拒绝；模板流：拒绝
        if (ctx == null || !ctx.isAutopilotEnabled()) {
            return FlowDesignToolSupport.errorJson("当前为半自动，不能调用 run_test_flow；请用户切换到全自动");
        }
        if (ctx.isTemplateDesignMode()) {
            return FlowDesignToolSupport.errorJson("模板预制流不支持 run_test_flow");
        }
        if (ctx.getTestFlowId() == null) {
            return FlowDesignToolSupport.errorJson("缺少 testFlowId");
        }
        // 未确认的素材或鉴权 Profile 提案会挡住跑流
        String pendingUpsert = ctx.pendingUpsertBlockReason();
        if (pendingUpsert != null) {
            return FlowDesignToolSupport.errorJson(pendingUpsert + "，请先让用户确认后再 run");
        }

        // 有未落盘 submit_* 时先写库，再跑库中最新图
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

        // 工具参数未传场景/环境时，用设计请求带来的默认值
        String runScenarioId = FlowDesignToolSupport.stringArg(
                arguments != null ? arguments.get("runScenarioId") : null);
        if (runScenarioId != null && runScenarioId.isBlank()) {
            runScenarioId = null;
        }
        if (runScenarioId == null) {
            runScenarioId = ctx.getDefaultRunScenarioId();
        }
        Long envId = FlowDesignToolSupport.longArg(
                arguments != null ? arguments.get("testProjectEnvId") : null);
        if (envId == null) {
            envId = ctx.getDefaultTestProjectEnvId();
        }

        Long runId;
        try {
            // triggerType=ai：运行库区分来源；trigger 立刻返回 runId，图在后台继续跑
            TriggerTestFlowRunParams params = TriggerTestFlowRunParams.builder()
                    .testFlowId(ctx.getTestFlowId())
                    .runScenarioId(runScenarioId)
                    .testProjectEnvId(envId)
                    .triggerType("ai")
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
        }

        // 先通知画布按 runId 开始高亮；本工具再等终态，把结果回给模型
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
