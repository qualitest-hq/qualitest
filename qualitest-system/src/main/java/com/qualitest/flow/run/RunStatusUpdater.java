package com.qualitest.flow.run;

import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.FlowRunContextPersistence;
import com.qualitest.flow.snapshot.FlowRunSnapshotState;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.runhub.EnvRunSlotLifecycle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 测试流 Run 状态落库：暂停、完成、中止、从暂停恢复为 running。
 * <p>
 * 只负责拼装并更新 {@code test_flow_run} 字段。
 * 进入终态（完成 / 中止）后，再交给环境槽生命周期：清活动项、释放环境占用、开跑下一项排队。
 */
@Service
@RequiredArgsConstructor
public class RunStatusUpdater {

    /** Run 行更新 / 乐观锁续跑 */
    private final RunPersistenceService runPersistenceService;
    /** 终态后释放环境槽并提升排队 */
    private final EnvRunSlotLifecycle envRunSlotLifecycle;

    /**
     * 标记 Run 为 paused：写入续跑所需上下文、快照栈、下一 step_index、错误信息。
     */
    public void markPaused(
            Long testFlowRunId,
            Date startedAt,
            long runT0,
            FlowRunContext ctx,
            FlowRunSnapshotState snapshotState,
            long nextStepIndex,
            String pauseNodeId,
            String pauseReason,
            String currentNodeId,
            String incomingEdgeId,
            String errorCode,
            String errorMessage
    ) {
        RunExecutionState state = RunExecutionState.builder()
                .nextStepIndex(nextStepIndex)
                .currentNodeId(currentNodeId)
                .incomingEdgeId(incomingEdgeId)
                .pauseReason(pauseReason)
                .pauseNodeId(pauseNodeId)
                .snapshotStack(snapshotState.entries())
                .context(FlowRunContextPersistence.toMap(ctx))
                .build();

        Date now = DateUtils.getNowDate();
        TestFlowRun update = new TestFlowRun();
        update.setTestFlowRunId(testFlowRunId);
        update.setStatus(RunStatus.PAUSED.getCode());
        update.setStartedAt(startedAt);
        update.setFinishedAt(null);
        update.setDurationMs(Math.max(0, System.currentTimeMillis() - runT0));
        update.setFlowSnapshot(com.alibaba.fastjson2.JSON.toJSONString(ctx.getFlow()));
        update.setRunExecutionState(state.toJson());
        update.setPausedAt(now);
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.setUpdateTime(now);
        runPersistenceService.updateRun(update);
    }

    /**
     * 标记 Run 终态（passed / failed）：写入 flow 快照、耗时，清空 run_execution_state，
     * 并通知环境槽生命周期释放占用、尝试开下一项排队。
     */
    public void markFinished(Long testFlowRunId, String status, Date startedAt, long runT0,
                             FlowRunContext ctx, String errorCode, String errorMessage) {
        Date finishedAt = DateUtils.getNowDate();
        TestFlowRun update = new TestFlowRun();
        update.setTestFlowRunId(testFlowRunId);
        update.setStatus(status);
        update.setStartedAt(startedAt);
        update.setFinishedAt(finishedAt);
        update.setDurationMs(Math.max(0, System.currentTimeMillis() - runT0));
        update.setFlowSnapshot(com.alibaba.fastjson2.JSON.toJSONString(ctx.getFlow()));
        update.setErrorCode(errorCode);
        update.setErrorMessage(errorMessage);
        update.setClearExecutionState(true);
        update.setUpdateTime(finishedAt);
        runPersistenceService.updateRun(update);
        envRunSlotLifecycle.onRunTerminal(testFlowRunId, ctx != null ? ctx.getTestProjectId() : null);
    }

    /**
     * 用户主动中止或业务判定中止：落 aborted 态后同样释放环境槽并提升排队。
     */
    public void markAborted(Long testFlowRunId, Date startedAt, long elapsedMs, String errorCode, String message) {
        Date finishedAt = DateUtils.getNowDate();
        TestFlowRun update = new TestFlowRun();
        update.setTestFlowRunId(testFlowRunId);
        update.setStatus(RunStatus.ABORTED.getCode());
        update.setStartedAt(startedAt);
        update.setFinishedAt(finishedAt);
        update.setDurationMs(Math.max(0, elapsedMs));
        update.setErrorCode(errorCode);
        update.setErrorMessage(message);
        update.setClearExecutionState(true);
        update.setUpdateTime(finishedAt);
        runPersistenceService.updateRun(update);
        envRunSlotLifecycle.onRunTerminal(testFlowRunId, null);
    }

    /** 乐观锁续跑：仅当当前为 paused 时切为 running。 */
    public boolean markRunningIfPaused(Long testFlowRunId) {
        return runPersistenceService.casMarkRunningFromPaused(testFlowRunId);
    }
}
