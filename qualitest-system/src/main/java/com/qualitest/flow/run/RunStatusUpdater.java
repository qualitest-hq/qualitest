package com.qualitest.flow.run;

import com.qualitest.common.utils.DateUtils;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.FlowRunContextPersistence;
import com.qualitest.flow.snapshot.FlowRunSnapshotState;
import com.qualitest.project.domain.TestFlowRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 测试流 Run 状态落库：暂停、完成、中止、恢复运行。
 * 统一拼装 test_flow_run 更新字段，避免各调用方重复写库逻辑。
 */
@Service
@RequiredArgsConstructor
public class RunStatusUpdater {

    private final RunPersistenceService runPersistenceService;

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
     * 标记 Run 终态（passed / failed）：写入 flow 快照、耗时，并清空 run_execution_state。
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
    }

    /** 用户主动中止或业务判定中止 */
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
    }

    /** 乐观锁续跑：仅当当前为 paused 时切为 running。 */
    public boolean markRunningIfPaused(Long testFlowRunId) {
        return runPersistenceService.casMarkRunningFromPaused(testFlowRunId);
    }
}
