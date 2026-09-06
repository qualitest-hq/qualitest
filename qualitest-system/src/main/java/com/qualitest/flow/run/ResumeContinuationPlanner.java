package com.qualitest.flow.run;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.snapshot.FlowRunSnapshotState;
import com.qualitest.flow.snapshot.SnapshotRestoreService;
import com.qualitest.flow.snapshot.SnapshotStackEntry;
import com.qualitest.project.domain.TestProjectEnv;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 将用户 resume 决策翻译为续跑计划。
 * <p>
 * 输出：续跑起点节点、入边、模式（重试 / 跳过 / 完成人工输入节点），以及可选的还原审计步。
 */
@Component
@RequiredArgsConstructor
public class ResumeContinuationPlanner {

    private final SnapshotRestoreService snapshotRestoreService;

    /**
     * 根据 decision 与当前暂停状态生成续跑计划。
     * <ul>
     *   <li>restoreAndRetry：按需还原快照、截断栈，从快照节点 RETRY_NODE</li>
     *   <li>retryInPlace：从暂停节点 RETRY_NODE</li>
     *   <li>skip：从暂停节点 SKIP_NODE</li>
     *   <li>continueWithInput：仅当 pauseReason=await_input；COMPLETE_NODE（flow 须已由调用方写好）</li>
     * </ul>
     * await_input 下除 continueWithInput / abort 外的决策一律拒绝。
     */
    public PlannedResume plan(
            ResumeDecision decision,
            RunExecutionState state,
            GraphJson graph,
            FlowRunSnapshotState snapshotState,
            TestProjectEnv env,
            Long testFlowRunId
    ) {
        if (RunExecutionState.PAUSE_REASON_AWAIT_INPUT.equals(state.getPauseReason())
                && !decision.isContinueWithInput()
                && !decision.isAbort()) {
            throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID,
                    "await_input 仅支持 continueWithInput 或 abort");
        }
        if (decision.isContinueWithInput()) {
            if (!RunExecutionState.PAUSE_REASON_AWAIT_INPUT.equals(state.getPauseReason())) {
                throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID,
                        "continueWithInput 仅用于 await_input 暂停");
            }
            if (state.getPauseNodeId() == null || state.getPauseNodeId().isBlank()) {
                throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID, "缺少 pauseNodeId");
            }
            return PlannedResume.of(RunContinuation.builder()
                    .startNodeId(state.getPauseNodeId())
                    .incomingEdgeId(state.getIncomingEdgeId())
                    .resumeMode(RunContinuation.ResumeMode.COMPLETE_NODE)
                    .completionAssigns(decision.getCompletionAssigns())
                    .build(), null);
        }
        if (decision.isRestoreAndRetry()) {
            String snapshotId = resolveSnapshotId(decision, snapshotState, state.getPauseNodeId());
            SnapshotStackEntry entry = snapshotState.findBySnapshotId(snapshotId)
                    .orElseThrow(() -> new FlowExecutionException(
                            FlowErrorCode.TF_RUN_RESUME_INVALID, "快照不在栈中: " + snapshotId));
            StepResult restoreStep = snapshotRestoreService.restore(env, testFlowRunId, snapshotId, entry.getNodeId());
            snapshotState.truncateAfter(snapshotId);
            GraphWalker walker = new GraphWalker(graph);
            return PlannedResume.of(
                    RunContinuation.builder()
                            .startNodeId(entry.getNodeId())
                            .incomingEdgeId(walker.findIncomingEdgeId(entry.getNodeId()))
                            .resumeMode(RunContinuation.ResumeMode.RETRY_NODE)
                            .build(),
                    restoreStep);
        }
        if (decision.isRetryInPlace()) {
            return PlannedResume.of(RunContinuation.builder()
                    .startNodeId(state.getPauseNodeId())
                    .incomingEdgeId(state.getIncomingEdgeId())
                    .resumeMode(RunContinuation.ResumeMode.RETRY_NODE)
                    .build(), null);
        }
        if (decision.isSkip()) {
            return PlannedResume.of(RunContinuation.builder()
                    .startNodeId(state.getPauseNodeId())
                    .incomingEdgeId(state.getIncomingEdgeId())
                    .resumeMode(RunContinuation.ResumeMode.SKIP_NODE)
                    .build(), null);
        }
        throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID,
                "不支持的 decision: " + decision.getDecision());
    }

    /**
     * 解析要还原的 snapshotId：优先用请求指定；否则取暂停节点最后一次 checkpoint；再否则取栈顶。
     */
    static String resolveSnapshotId(ResumeDecision decision, FlowRunSnapshotState stack, String pauseNodeId) {
        if (decision.getSnapshotId() != null && !decision.getSnapshotId().isBlank()) {
            return decision.getSnapshotId();
        }
        Optional<SnapshotStackEntry> forNode = stack.entries().stream()
                .filter(e -> pauseNodeId != null && pauseNodeId.equals(e.getNodeId()))
                .reduce((first, second) -> second);
        if (forNode.isPresent()) {
            return forNode.get().getSnapshotId();
        }
        SnapshotStackEntry peek = stack.peek();
        if (peek != null) {
            return peek.getSnapshotId();
        }
        throw new FlowExecutionException(FlowErrorCode.TF_RUN_RESUME_INVALID, "未指定 snapshotId 且快照栈为空");
    }

    /** 续跑计划：图遍历起点 + 可选还原审计步 */
    @Getter
    public static class PlannedResume {
        private final RunContinuation continuation;
        /** 还原审计步；未执行还原时为 null */
        private final StepResult restoreStep;

        private PlannedResume(RunContinuation continuation, StepResult restoreStep) {
            this.continuation = continuation;
            this.restoreStep = restoreStep;
        }

        public static PlannedResume of(RunContinuation continuation, StepResult restoreStep) {
            return new PlannedResume(continuation, restoreStep);
        }
    }
}
