package com.qualitest.flow.run;

import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.FlowRunContextPersistence;
import com.qualitest.flow.context.ResolvedRunScenario;
import com.qualitest.flow.context.RunScenarioBootstrap;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.input.InputFieldTypes;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.snapshot.FlowRunSnapshotState;
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.domain.TestFlowRunStep;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.service.ITestFlowRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 测试流 Run 执行器（薄编排）。
 * <p>
 * 职责：触发首次执行或 paused 续跑 → 委托图遍历、checkpoint 钩子、状态落库。
 * 不直接调用被测 HTTP；快照/还原由 snapshot 包与钩子完成。
 */
@Service
@RequiredArgsConstructor
public class TestFlowExecutor {

    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final FlowGraphRunner flowGraphRunner;
    private final ITestFlowRunService testFlowRunService;
    private final RunPersistenceService runPersistenceService;
    private final RunStatusUpdater runStatusUpdater;
    private final StepResultWriter stepResultWriter;
    private final SnapshotPreExecuteHookFactory snapshotPreExecuteHookFactory;
    private final ResumeContinuationPlanner resumeContinuationPlanner;

    /**
     * 首次执行 Run：写 runConfig 首步 → 从图起点遍历。
     * 异常统一由 {@link #runWithExceptionGuard} 兜底为 failed 终态。
     */
    public ExecutionOutcome execute(Long testFlowRunId, GraphJson snapshot, FlowRunContext ctx,
                                    RunBootstrapMeta bootstrap) {
        long runT0 = System.currentTimeMillis();
        Date startedAt = DateUtils.getNowDate();
        return runWithExceptionGuard(testFlowRunId, startedAt, runT0, ctx, () -> {
            if (bootstrap != null && bootstrap.getScenario() != null) {
                TestFlowRunStep bootstrapStep = stepResultWriter.toRunConfigStepEntity(
                        testFlowRunId, bootstrap.getScenario(), bootstrap.getEnvName(), ctx);
                runPersistenceService.insertStep(bootstrapStep);
            }
            return advanceRun(testFlowRunId, snapshot, ctx, bootstrap, RunContinuation.fresh(),
                    new FlowRunSnapshotState(), 1L, startedAt, runT0);
        });
    }

    /**
     * 续跑 paused 的 Run：记决策审计步 →（人工输入则先写 flow）→ 按 decision 规划 → 恢复图遍历。
     * 非 paused 返回幂等结果；参数非法抛异常。
     */
    public ExecutionOutcome resume(Long testFlowRunId, ResumeDecision decision, TestProjectEnv env) {
        TestFlowRun run = testFlowRunService.selectTestFlowRunById(testFlowRunId);
        if (run == null) {
            throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID, "运行记录不存在");
        }
        if (!RunStatus.PAUSED.equals(run.getStatus())) {
            return ExecutionOutcome.idempotent(run.getStatus());
        }
        if (decision == null || decision.getDecision() == null || decision.getDecision().isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID, "decision 不能为空");
        }

        RunExecutionState state = RunExecutionState.fromJson(run.getRunExecutionState());
        if (state == null) {
            throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID, "缺少 run_execution_state");
        }

        long nextStepIndex = state.getNextStepIndex() > 0 ? state.getNextStepIndex() : 1L;
        persistStep(testFlowRunId, nextStepIndex++, stepResultWriter.toResumeDecisionStepResult(decision));

        if (decision.isAbort()) {
            runStatusUpdater.markAborted(testFlowRunId, run.getStartedAt(), elapsedMs(run),
                    FlowErrorCode.TF_RUN_RESUME_INVALID.getCode(), "用户中止");
            return ExecutionOutcome.aborted();
        }

        FlowRunSnapshotState snapshotState = FlowRunSnapshotState.fromEntries(state.getSnapshotStack());
        GraphJson graph = GraphJson.parse(run.getGraphJsonSnapshot());
        FlowRunContext ctx = FlowRunContextPersistence.fromMap(state.getContext());
        RunBootstrapMeta bootstrap = buildBootstrapFromRun(run, env);

        if (decision.isContinueWithInput()) {
            applyContinueWithInput(decision, state, graph, ctx);
        }

        ResumeContinuationPlanner.PlannedResume planned = resumeContinuationPlanner.plan(
                decision, state, graph, snapshotState, env, testFlowRunId);
        if (planned.getRestoreStep() != null) {
            persistStep(testFlowRunId, nextStepIndex++, planned.getRestoreStep());
        }

        if (!runStatusUpdater.markRunningIfPaused(testFlowRunId)) {
            TestFlowRun latest = testFlowRunService.selectTestFlowRunById(testFlowRunId);
            return ExecutionOutcome.idempotent(latest != null ? latest.getStatus() : run.getStatus());
        }

        long runT0 = run.getStartedAt() != null ? run.getStartedAt().getTime() : System.currentTimeMillis();
        final long resumeFromStepIndex = nextStepIndex;
        return runWithExceptionGuard(testFlowRunId, run.getStartedAt(), runT0, ctx, () ->
                advanceRun(testFlowRunId, graph, ctx, bootstrap, planned.getContinuation(), snapshotState,
                        resumeFromStepIndex, run.getStartedAt(), runT0));
    }

    /**
     * continueWithInput：按暂停节点 fields 校验 inputs，写入 ctx.flow，
     * 并把 assigns 明细放到 decision.completionAssigns 供 COMPLETE_NODE 落库。
     */
    private void applyContinueWithInput(
            ResumeDecision decision,
            RunExecutionState state,
            GraphJson graph,
            FlowRunContext ctx
    ) {
        if (!RunExecutionState.PAUSE_REASON_AWAIT_INPUT.equals(state.getPauseReason())) {
            throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID,
                    "continueWithInput 仅用于 await_input 暂停");
        }
        GraphNode pauseNode = graph != null
                ? GraphLookupUtils.findNode(graph.getNodes(), state.getPauseNodeId())
                : null;
        if (pauseNode == null) {
            throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID, "暂停节点不存在");
        }
        Map<String, Object> data = pauseNode.getData() != null ? pauseNode.getData() : Map.of();
        List<Map<String, Object>> fields = InputFieldTypes.parseFields(data.get("fields"));
        List<Map<String, Object>> assigns = InputFieldTypes.validateAndApply(fields, decision.getInputs(), ctx.getFlow());
        decision.setCompletionAssigns(assigns);
    }

    /** 图遍历主循环：checkpoint 钩子 → 落库步骤 → 更新 Run 终态或 paused */
    private ExecutionOutcome advanceRun(
            Long testFlowRunId,
            GraphJson snapshot,
            FlowRunContext ctx,
            RunBootstrapMeta bootstrap,
            RunContinuation continuation,
            FlowRunSnapshotState snapshotState,
            long nextStepIndex,
            Date startedAt,
            long runT0
    ) {
        ResolvedRunScenario scenario = bootstrap != null ? bootstrap.getScenario() : null;
        TestProjectEnv env = bootstrap != null ? bootstrap.getEnv() : null;
        RunSnapshotPolicy policy = RunSnapshotPolicy.fromScenario(scenario);

        FlowNodePreExecuteHook snapshotHook = snapshotPreExecuteHookFactory.create(
                testFlowRunId, env, policy, snapshotState);

        FlowGraphRunner.Outcome outcome = flowGraphRunner.run(
                snapshot, ctx, nodeHandlerRegistry, snapshotHook, continuation, policy);

        long stepIndex = nextStepIndex;
        for (StepResult result : outcome.getSteps()) {
            persistStep(testFlowRunId, stepIndex++, result);
        }

        if (outcome.isPaused()) {
            String errorCode = outcome.getError() != null
                    ? outcome.getError().getCode()
                    : FlowErrorCode.TF_STEP_ERROR.getCode();
            String errorMessage = outcome.getError() != null
                    ? outcome.getError().getMessage()
                    : "步骤失败";
            if (RunExecutionState.PAUSE_REASON_AWAIT_INPUT.equals(outcome.getPauseReason())) {
                errorCode = FlowErrorCode.TF_AWAIT_INPUT.getCode();
                errorMessage = "等待人工输入";
            }
            runStatusUpdater.markPaused(testFlowRunId, startedAt, runT0, ctx, snapshotState, stepIndex,
                    outcome.getPauseNodeId(), outcome.getPauseReason(),
                    outcome.getCurrentNodeId(), outcome.getIncomingEdgeId(),
                    errorCode, errorMessage);
            return ExecutionOutcome.paused(errorCode, errorMessage, outcome.getExecutedSteps());
        }

        if (!outcome.isPassed()) {
            String errorCode = outcome.getError() != null
                    ? outcome.getError().getCode()
                    : FlowErrorCode.TF_STEP_ERROR.getCode();
            String errorMessage = outcome.getError() != null
                    ? outcome.getError().getMessage()
                    : "步骤失败";
            runStatusUpdater.markFinished(testFlowRunId, RunStatus.FAILED, startedAt, runT0, ctx, errorCode, errorMessage);
            return ExecutionOutcome.failed(errorCode, errorMessage, outcome.getExecutedSteps());
        }

        runStatusUpdater.markFinished(testFlowRunId, RunStatus.PASSED, startedAt, runT0, ctx, null, null);
        return ExecutionOutcome.passed(outcome.getExecutedSteps());
    }

    @FunctionalInterface
    private interface RunExecutionAction {
        ExecutionOutcome run() throws Exception;
    }

    /** 统一异常兜底：尽力将 Run 标记为 failed，避免长期停留在 running */
    private ExecutionOutcome runWithExceptionGuard(
            Long testFlowRunId,
            Date startedAt,
            long runT0,
            FlowRunContext ctx,
            RunExecutionAction action
    ) {
        try {
            return action.run();
        } catch (Exception e) {
            return handleRunException(testFlowRunId, startedAt, runT0, ctx, e);
        }
    }

    private ExecutionOutcome handleRunException(Long testFlowRunId, Date startedAt, long runT0,
                                                FlowRunContext ctx, Exception e) {
        String errorCode = FlowErrorCode.TF_STEP_ERROR.getCode();
        String errorMessage = "运行异常";
        if (e instanceof FlowExecutionException fex) {
            errorCode = fex.getErrorCode().getCode();
            errorMessage = fex.getMessage() != null ? fex.getMessage() : errorMessage;
        } else if (e.getMessage() != null && !e.getMessage().isBlank()) {
            errorMessage = e.getMessage();
        }
        FlowRunContext safeCtx = ctx != null ? ctx : new FlowRunContext();
        try {
            runStatusUpdater.markFinished(testFlowRunId, RunStatus.FAILED, startedAt, runT0, safeCtx, errorCode, errorMessage);
        } catch (Exception ignored) {
            // 尽力更新终态
        }
        return ExecutionOutcome.failed(errorCode, errorMessage, 0);
    }

    private void persistStep(Long testFlowRunId, long stepIndex, StepResult result) {
        TestFlowRunStep entity = stepResultWriter.toEntity(testFlowRunId, (int) stepIndex, result);
        runPersistenceService.insertStep(entity);
    }

    private static RunBootstrapMeta buildBootstrapFromRun(TestFlowRun run, TestProjectEnv env) {
        GraphJson graph = GraphJson.parse(run.getGraphJsonSnapshot());
        ResolvedRunScenario scenario = RunScenarioBootstrap.resolve(
                graph, run.getRunScenarioId(), run.getTestProjectEnvId());
        String envName = env != null ? env.getEnvName() : null;
        return new RunBootstrapMeta(scenario, envName, env);
    }

    private static long elapsedMs(TestFlowRun run) {
        if (run.getStartedAt() == null) {
            return 0L;
        }
        return Math.max(0, System.currentTimeMillis() - run.getStartedAt().getTime());
    }
}
