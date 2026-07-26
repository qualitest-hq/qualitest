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
 * 输出：从哪个节点继续、入边、续跑模式（重试/跳过），以及可选的还原审计步。
 */
@Component
@RequiredArgsConstructor
public class ResumeContinuationPlanner {

    private final SnapshotRestoreService snapshotRestoreService;

    /**
     * 根据 decision 生成续跑计划。
     * restoreAndRetry：还原（环境允许时）→ 截断快照栈 → 从快照节点重试。
     * retryInPlace：从暂停节点原地重试。
     * skip：跳过暂停节点，沿出边继续。
     */
    public PlannedResume plan(
            ResumeDecision decision,
            RunExecutionState state,
            GraphJson graph,
            FlowRunSnapshotState snapshotState,
            TestProjectEnv env,
            Long testFlowRunId
    ) {
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
