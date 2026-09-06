package com.qualitest.flow.run;

import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.snapshot.RunSnapshotPolicy;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 内存图遍历执行器：按边顺序执行节点 Handler，支持续跑与暂停。
 * <p>
 * 暂停来源：节点返回 failed 且策略要求暂停、checkpoint 钩子失败、节点返回 paused（人工输入）。
 * 续跑模式含重试、跳过、完成当前节点（人工输入已写入后记 passed）等。
 */
@Component
public class FlowGraphRunner {

    public Outcome run(GraphJson snapshot, FlowRunContext ctx, NodeHandlerRegistry registry) {
        return run(snapshot, ctx, registry, FlowNodePreExecuteHook.NONE, RunContinuation.fresh(), RunSnapshotPolicy.defaults());
    }

    public Outcome run(GraphJson snapshot, FlowRunContext ctx, NodeHandlerRegistry registry,
                       FlowNodePreExecuteHook preExecuteHook) {
        return run(snapshot, ctx, registry, preExecuteHook, RunContinuation.fresh(), RunSnapshotPolicy.defaults());
    }

    public Outcome run(GraphJson snapshot, FlowRunContext ctx, NodeHandlerRegistry registry,
                       FlowNodePreExecuteHook preExecuteHook, RunContinuation continuation,
                       RunSnapshotPolicy policy) {
        GraphWalker walker = new GraphWalker(snapshot);
        String currentId;
        String incomingEdgeId;
        boolean skipCurrentNode = continuation != null
                && continuation.getResumeMode() == RunContinuation.ResumeMode.SKIP_NODE;
        boolean completeCurrentNode = continuation != null
                && continuation.getResumeMode() == RunContinuation.ResumeMode.COMPLETE_NODE;

        if (continuation != null && !continuation.isFreshStart()) {
            currentId = continuation.getStartNodeId();
            incomingEdgeId = continuation.getIncomingEdgeId();
        } else {
            try {
                currentId = walker.findUniqueStartNodeId();
            } catch (FlowExecutionException e) {
                return Outcome.failed(List.of(), ctx, StepError.of(e.getErrorCode(), e.getMessage()), 0);
            }
            incomingEdgeId = null;
        }

        int stepIndex = 1;
        int maxSteps = GraphWalker.DEFAULT_MAX_STEPS;
        List<StepResult> steps = new ArrayList<>();

        while (currentId != null) {
            if (stepIndex > maxSteps) {
                FlowExecutionException ex = new FlowExecutionException(
                        FlowErrorCode.TF_RUN_STEP_LIMIT,
                        "超过步数上限: " + maxSteps
                );
                return Outcome.failed(steps, ctx, StepError.of(ex.getErrorCode(), ex.getMessage()), steps.size());
            }

            GraphNode node = walker.getNode(currentId);
            if (node == null) {
                FlowExecutionException ex = new FlowExecutionException(
                        FlowErrorCode.TF_GRAPH_INVALID,
                        "节点不存在: " + currentId
                );
                return Outcome.failed(steps, ctx, StepError.of(ex.getErrorCode(), ex.getMessage()), steps.size());
            }

            if (!skipCurrentNode && !completeCurrentNode && preExecuteHook != null && preExecuteHook != FlowNodePreExecuteHook.NONE) {
                FlowNodePreExecuteHook.PreExecuteOutcome pre = preExecuteHook.beforeNode(node);
                if (pre != null) {
                    for (StepResult prelude : pre.preludeSteps()) {
                        steps.add(prelude);
                    }
                    if (pre.pause()) {
                        StepError error = pre.abortError() != null
                                ? pre.abortError()
                                : StepError.of(FlowErrorCode.TF_SNAPSHOT_FAILED, "checkpoint 失败");
                        return Outcome.paused(steps, ctx, error, node.getId(),
                                RunExecutionState.PAUSE_REASON_SNAPSHOT_FAILURE, steps.size(),
                                currentId, incomingEdgeId);
                    }
                    if (pre.abort()) {
                        StepError error = pre.abortError() != null
                                ? pre.abortError()
                                : StepError.of(FlowErrorCode.TF_SNAPSHOT_FAILED, "checkpoint 失败");
                        return Outcome.failed(steps, ctx, error, steps.size());
                    }
                }
            }

            StepResult result;
            if (skipCurrentNode) {
                result = syntheticStep(node, incomingEdgeId, ctx,
                        StepResultWriter.STATUS_SKIPPED, null, node.getId());
                skipCurrentNode = false;
            } else if (completeCurrentNode) {
                result = syntheticStep(node, incomingEdgeId, ctx,
                        StepResult.STATUS_PASSED,
                        continuation != null ? continuation.getCompletionAssigns() : null,
                        resolveDisplayName(node));
                completeCurrentNode = false;
            } else {
                result = registry.execute(ctx, node, incomingEdgeId);
            }
            steps.add(result);

            if (StepResult.STATUS_PAUSED.equals(result.getStatus())) {
                StepError error = result.getError() != null
                        ? result.getError()
                        : StepError.of(FlowErrorCode.TF_AWAIT_INPUT, "等待人工输入");
                return Outcome.paused(steps, ctx, error, node.getId(),
                        RunExecutionState.PAUSE_REASON_AWAIT_INPUT, steps.size(),
                        currentId, incomingEdgeId);
            }

            if (StepResult.STATUS_FAILED.equals(result.getStatus())) {
                StepError error = result.getError() != null
                        ? result.getError()
                        : StepError.of(FlowErrorCode.TF_STEP_ERROR, "步骤失败");
                if (policy != null && policy.shouldPauseOnNodeFailure()) {
                    return Outcome.paused(steps, ctx, error, node.getId(),
                            RunExecutionState.PAUSE_REASON_NODE_FAILURE, steps.size(),
                            currentId, incomingEdgeId);
                }
                return Outcome.failed(steps, ctx, error, steps.size());
            }

            stepIndex++;
            String fromId = currentId;
            currentId = walker.resolveNextNodeId(node, result);
            incomingEdgeId = currentId != null ? walker.findEdgeId(fromId, currentId) : null;
        }

        return Outcome.passed(steps, ctx, steps.size());
    }

    /** 跳过或完成节点时不跑 Handler，直接合成一步（skipped 或 passed） */
    private static StepResult syntheticStep(
            GraphNode node,
            String incomingEdgeId,
            FlowRunContext ctx,
            String status,
            Object assigns,
            String nodeName
    ) {
        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(node.getType())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(status)
                .durationMs(0L)
                .flowAfter(new HashMap<>(ctx.getFlow()))
                .assigns(assigns)
                .build();
    }

    /** 优先 data.name，否则用节点 id */
    private static String resolveDisplayName(GraphNode node) {
        if (node.getData() != null && node.getData().get("name") != null) {
            String name = String.valueOf(node.getData().get("name")).trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return node.getId();
    }

    @Getter
    public static class Outcome {
        private final boolean passed;
        private final boolean paused;
        private final List<StepResult> steps;
        private final FlowRunContext ctx;
        private final StepError error;
        private final int executedSteps;
        private final String pauseNodeId;
        private final String pauseReason;
        private final String currentNodeId;
        private final String incomingEdgeId;

        private Outcome(boolean passed, boolean paused, List<StepResult> steps, FlowRunContext ctx,
                        StepError error, int executedSteps, String pauseNodeId, String pauseReason,
                        String currentNodeId, String incomingEdgeId) {
            this.passed = passed;
            this.paused = paused;
            this.steps = steps != null ? List.copyOf(steps) : List.of();
            this.ctx = ctx;
            this.error = error;
            this.executedSteps = executedSteps;
            this.pauseNodeId = pauseNodeId;
            this.pauseReason = pauseReason;
            this.currentNodeId = currentNodeId;
            this.incomingEdgeId = incomingEdgeId;
        }

        public static Outcome passed(List<StepResult> steps, FlowRunContext ctx, int executedSteps) {
            return new Outcome(true, false, steps, ctx, null, executedSteps, null, null, null, null);
        }

        public static Outcome failed(List<StepResult> steps, FlowRunContext ctx, StepError error, int executedSteps) {
            return new Outcome(false, false, steps, ctx, error, executedSteps, null, null, null, null);
        }

        public static Outcome paused(List<StepResult> steps, FlowRunContext ctx, StepError error,
                                       String pauseNodeId, String pauseReason, int executedSteps,
                                       String currentNodeId, String incomingEdgeId) {
            return new Outcome(false, true, steps, ctx, error, executedSteps, pauseNodeId, pauseReason,
                    currentNodeId, incomingEdgeId);
        }
    }
}
