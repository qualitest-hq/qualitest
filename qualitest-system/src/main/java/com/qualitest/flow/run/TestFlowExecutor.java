package com.qualitest.flow.run;

import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.context.EntryRunPolicySupport;
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
import com.qualitest.plugin.enable.PluginEnableService;
import com.qualitest.plugin.prepare.PluginRunPrepareService;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.domain.TestFlowRunStep;
import com.qualitest.project.domain.TestPluginEnvBinding;
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
 * 职责：首次执行或 paused 续跑 → 委托图遍历与 checkpoint 钩子 → 按步写入步骤表、更新 Run 终态。
 * 每完成一个可展示步骤立即 insertStep，执行中即可查到已完成步骤；不直接发被测 HTTP。
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
    private final PluginRunPrepareService pluginRunPrepareService;
    private final PluginEnableService pluginEnableService;

    /**
     * 首次执行 Run：写 runConfig 首步 → onRunPrepare（如场景造数）→ 从图起点遍历。
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
            long nextStepIndex = 1L;
            if (bootstrap != null && bootstrap.getEnv() != null) {
                List<StepResult> prepareSteps = pluginRunPrepareService.runPrepare(
                        bootstrap.getEnv(), testFlowRunId);
                if (prepareSteps != null) {
                    for (StepResult prepareStep : prepareSteps) {
                        persistStep(testFlowRunId, nextStepIndex++, prepareStep);
                    }
                }
            }
            return advanceRun(testFlowRunId, snapshot, ctx, bootstrap, RunContinuation.fresh(),
                    new FlowRunSnapshotState(), nextStepIndex, startedAt, runT0);
        });
    }

    /**
     * 续跑 paused 的 Run。
     * 流程：落决策审计步 → continueWithInput 时先按字段定义校验写入 flow →
     * 按 decision 规划续跑起点 → 置回 running 并继续图遍历。
     * 非 paused 返回幂等结果；参数非法抛异常。
     */
    public ExecutionOutcome resume(Long testFlowRunId, ResumeDecision decision, TestProjectEnv env) {
        TestFlowRun run = testFlowRunService.selectTestFlowRunById(testFlowRunId);
        if (run == null) {
            throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID, "运行记录不存在");
        }
        if (!RunStatus.PAUSED.matches(run.getStatus())) {
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
     * continueWithInput 专用：按暂停 Input 节点的 fields 校验客户端 inputs，写入 ctx.flow；
     * assigns 明细挂到 decision.completionAssigns，供 COMPLETE_NODE 模式落库通过步。
     * 暂停原因不是 await_input、或节点找不到时抛非法续跑。
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
        List<TestPluginEnvBinding> bindings = null;
        if (env != null && env.getTestProjectEnvId() != null) {
            bindings = pluginEnableService.listEnabledBindings(env.getTestProjectEnvId());
        }
        RunSnapshotPolicy policy = EntryRunPolicySupport.compose(
                snapshot, EntryRunPolicySupport.resolveEntryNodeId(ctx, scenario), bindings);

        FlowNodePreExecuteHook snapshotHook = snapshotPreExecuteHookFactory.create(
                testFlowRunId, env, policy, snapshotState);

        // 每完成一步立刻写入步骤表（含 step_index），不再在整段跑完后批量补插
        java.util.concurrent.atomic.AtomicLong stepIndexRef =
                new java.util.concurrent.atomic.AtomicLong(nextStepIndex);
        FlowGraphRunner.Outcome outcome = flowGraphRunner.run(
                snapshot, ctx, nodeHandlerRegistry, snapshotHook, continuation, policy,
                result -> persistStep(testFlowRunId, stepIndexRef.getAndIncrement(), result));
        long stepIndex = stepIndexRef.get();

        // 图遍历返回暂停：落 run_execution_state（含上下文快照），头状态 paused
        if (outcome.isPaused()) {
            String errorCode = outcome.getError() != null
                    ? outcome.getError().getCode()
                    : FlowErrorCode.TF_STEP_ERROR.getCode();
            String errorMessage = outcome.getError() != null
                    ? outcome.getError().getMessage()
                    : "步骤失败";
            // 填表暂停统一对外错误码 / 文案，便于前端识别
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
            runStatusUpdater.markFinished(testFlowRunId, RunStatus.FAILED.getCode(), startedAt, runT0, ctx, errorCode, errorMessage);
            return ExecutionOutcome.failed(errorCode, errorMessage, outcome.getExecutedSteps());
        }

        runStatusUpdater.markFinished(testFlowRunId, RunStatus.PASSED.getCode(), startedAt, runT0, ctx, null, null);
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
            runStatusUpdater.markFinished(testFlowRunId, RunStatus.FAILED.getCode(), startedAt, runT0, safeCtx, errorCode, errorMessage);
        } catch (Exception ignored) {
            // 尽力更新终态
        }
        return ExecutionOutcome.failed(errorCode, errorMessage, 0);
    }

    /** 将单步结果写成 test_flow_run_step 并立即入库 */
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
