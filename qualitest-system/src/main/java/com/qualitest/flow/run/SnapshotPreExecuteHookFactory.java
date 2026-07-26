package com.qualitest.flow.run;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.snapshot.CheckpointAttempt;
import com.qualitest.flow.snapshot.FlowRunSnapshotState;
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
import com.qualitest.flow.snapshot.SnapshotCheckpointService;
import com.qualitest.project.domain.TestProjectEnv;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 为图遍历器构建「节点执行前 checkpoint」钩子。
 * <p>
 * 每个业务节点执行前：尝试打快照 → 写审计步 → 按策略决定继续、暂停或中止。
 * 无需快照或环境未允许还原时，钩子直接跳过，不影响节点执行。
 */
@Component
@RequiredArgsConstructor
public class SnapshotPreExecuteHookFactory {

    private final SnapshotCheckpointService snapshotCheckpointService;
    private final StepResultWriter stepResultWriter;

    public FlowNodePreExecuteHook create(
            Long testFlowRunId,
            TestProjectEnv env,
            RunSnapshotPolicy policy,
            FlowRunSnapshotState snapshotState
    ) {
        return node -> {
            CheckpointAttempt attempt = snapshotCheckpointService.maybeCheckpoint(
                    node, testFlowRunId, env, policy, snapshotState);
            if (attempt == null) {
                return FlowNodePreExecuteHook.PreExecuteOutcome.skip();
            }
            StepResult snapshotStep = stepResultWriter.toSnapshotStepResult(attempt);
            if (attempt.isPassed()) {
                return FlowNodePreExecuteHook.PreExecuteOutcome.ok(List.of(snapshotStep));
            }
            if (attempt.shouldContinueDespiteFailure()) {
                return FlowNodePreExecuteHook.PreExecuteOutcome.ok(List.of(snapshotStep));
            }
            StepError error = StepError.of(
                    attempt.getErrorCode() != null
                            ? attempt.getErrorCode()
                            : FlowErrorCode.TF_SNAPSHOT_FAILED,
                    attempt.getErrorMessage());
            if (attempt.isShouldPause()) {
                return FlowNodePreExecuteHook.PreExecuteOutcome.pause(error, List.of(snapshotStep));
            }
            return FlowNodePreExecuteHook.PreExecuteOutcome.abort(error, List.of(snapshotStep));
        };
    }
}
