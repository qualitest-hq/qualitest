package com.qualitest.flow.snapshot;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import lombok.Getter;

/**
 * 单次 checkpoint 尝试的结果。
 * <p>
 * 用于生成快照审计步，并决定 checkpoint 失败后是继续、暂停还是中止 Run。
 */
@Getter
public class CheckpointAttempt {

    private final GraphNode node;
    private final boolean passed;
    private final SnapshotRef snapshotRef;
    private final FlowErrorCode errorCode;
    private final String errorMessage;
    private final String resetEndpointBase;
    private final SnapshotScope scope;
    private final String label;
    private final long durationMs;
    /** checkpoint 失败且策略为 abort 时为 true */
    private final boolean abortRun;
    /** checkpoint 失败且策略为 prompt 时为 true */
    private final boolean shouldPause;

    private CheckpointAttempt(
            GraphNode node,
            boolean passed,
            SnapshotRef snapshotRef,
            FlowErrorCode errorCode,
            String errorMessage,
            String resetEndpointBase,
            SnapshotScope scope,
            String label,
            long durationMs,
            boolean abortRun,
            boolean shouldPause
    ) {
        this.node = node;
        this.passed = passed;
        this.snapshotRef = snapshotRef;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.resetEndpointBase = resetEndpointBase;
        this.scope = scope;
        this.label = label;
        this.durationMs = durationMs;
        this.abortRun = abortRun;
        this.shouldPause = shouldPause;
    }

    public static CheckpointAttempt success(
            GraphNode node,
            SnapshotRef ref,
            String resetBase,
            SnapshotScope scope,
            String label,
            long durationMs
    ) {
        return new CheckpointAttempt(node, true, ref, null, null, resetBase, scope, label, durationMs, false, false);
    }

    public static CheckpointAttempt failure(
            GraphNode node,
            FlowErrorCode errorCode,
            String errorMessage,
            String resetBase,
            SnapshotScope scope,
            String label,
            long durationMs,
            RunSnapshotPolicy policy
    ) {
        RunSnapshotPolicy effective = policy != null ? policy : RunSnapshotPolicy.defaults();
        return new CheckpointAttempt(
                node, false, null, errorCode, errorMessage, resetBase, scope, label, durationMs,
                effective.shouldAbortOnSnapshotFailure(),
                effective.shouldPauseOnSnapshotFailure());
    }

    /**
     * checkpoint 失败但策略为 continue：记失败审计步后仍执行节点业务逻辑。
     */
    public boolean shouldContinueDespiteFailure() {
        return !passed && !abortRun && !shouldPause;
    }
}
