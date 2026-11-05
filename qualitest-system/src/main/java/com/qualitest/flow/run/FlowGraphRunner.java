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
import com.qualitest.flow.validate.FlowNodeType;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 内存图遍历执行器：按边顺序执行节点 Handler，支持续跑、暂停与失败后继续。
 * <p>
 * 每产生一步可立刻回调以便落库；内存中保留完整 steps。
 * 暂停来源：节点失败且策略要求暂停、checkpoint 钩子失败、节点 paused（人工输入）。
 * 续跑模式含重试、跳过、完成当前节点等。
 * <p>
 * 业务步失败且不暂停时进入失败后继续：沿出边前进；
 * 未标 alwaysRun 记 bypassed；alwaysRun 成功记 continued、失败记 continued_failed 且不中断后续；
 * 未求值的 condition 记 bypassed 并对各分支 target 扇出；Run 主错误保留首个业务失败。
 */
@Component
public class FlowGraphRunner {

    /** 使用默认钩子、全新续跑与默认策略执行图 */
    public Outcome run(GraphJson snapshot, FlowRunContext ctx, NodeHandlerRegistry registry) {
        return run(snapshot, ctx, registry, FlowNodePreExecuteHook.NONE, RunContinuation.fresh(),
                RunSnapshotPolicy.defaults(), null);
    }

    /** 指定节点执行前钩子，其余用默认续跑与策略 */
    public Outcome run(GraphJson snapshot, FlowRunContext ctx, NodeHandlerRegistry registry,
                       FlowNodePreExecuteHook preExecuteHook) {
        return run(snapshot, ctx, registry, preExecuteHook, RunContinuation.fresh(),
                RunSnapshotPolicy.defaults(), null);
    }

    /** 指定钩子、续跑信息与运行策略，无逐步回调 */
    public Outcome run(GraphJson snapshot, FlowRunContext ctx, NodeHandlerRegistry registry,
                       FlowNodePreExecuteHook preExecuteHook, RunContinuation continuation,
                       RunSnapshotPolicy policy) {
        return run(snapshot, ctx, registry, preExecuteHook, continuation, policy, null);
    }

    /**
     * 执行图快照。
     *
     * @param snapshot       固化图
     * @param ctx            运行上下文
     * @param registry       节点 Handler 注册表
     * @param preExecuteHook 节点执行前钩子，可为 NONE
     * @param continuation   续跑起点与模式
     * @param policy         运行策略（步数上限、失败是否暂停等）
     * @param onStep         每产生一步立刻回调，便于落库；可为 null
     * @return 通过、失败或暂停结果；失败时 error 为首个业务失败
     */
    public Outcome run(GraphJson snapshot, FlowRunContext ctx, NodeHandlerRegistry registry,
                       FlowNodePreExecuteHook preExecuteHook, RunContinuation continuation,
                       RunSnapshotPolicy policy, Consumer<StepResult> onStep) {
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
        // 步数上限来自运行策略（由场景 maxSteps 解析）；策略缺失时用遍历器默认值
        int maxSteps = policy != null && policy.getMaxSteps() > 0
                ? policy.getMaxSteps()
                : GraphWalker.DEFAULT_MAX_STEPS;
        List<StepResult> steps = new ArrayList<>();
        // 本 Run 已走过的节点，供失败后继续去重与回边终止
        Set<String> visitedNodeIds = new HashSet<>();

        while (currentId != null) {
            GraphNode node = walker.getNode(currentId);
            if (node == null) {
                FlowExecutionException ex = new FlowExecutionException(
                        FlowErrorCode.TF_GRAPH_INVALID,
                        "节点不存在: " + currentId
                );
                return Outcome.failed(steps, ctx, StepError.of(ex.getErrorCode(), ex.getMessage()), steps.size());
            }

            // 下一步将超出上限：不执行该节点 Handler，合成一条失败步并终止 Run
            if (stepIndex > maxSteps) {
                StepError limitError = stepLimitError(node, currentId, maxSteps);
                visitedNodeIds.add(currentId);
                recordStep(steps, errorStep(node, incomingEdgeId, ctx,
                        RunStatus.FAILED.getCode(), limitError), onStep);
                return Outcome.failed(steps, ctx, limitError, steps.size());
            }

            if (!skipCurrentNode && !completeCurrentNode && preExecuteHook != null && preExecuteHook != FlowNodePreExecuteHook.NONE) {
                FlowNodePreExecuteHook.PreExecuteOutcome pre = preExecuteHook.beforeNode(node);
                if (pre != null) {
                    for (StepResult prelude : pre.preludeSteps()) {
                        recordStep(steps, prelude, onStep);
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
                        RunStatus.SKIPPED.getCode(), null, resolveDisplayName(node));
                skipCurrentNode = false;
            } else if (completeCurrentNode) {
                result = syntheticStep(node, incomingEdgeId, ctx,
                        RunStatus.PASSED.getCode(),
                        continuation != null ? continuation.getCompletionAssigns() : null,
                        resolveDisplayName(node));
                completeCurrentNode = false;
            } else {
                result = registry.execute(ctx, node, incomingEdgeId);
            }
            visitedNodeIds.add(currentId);
            recordStep(steps, result, onStep);

            if (RunStatus.PAUSED.matches(result.getStatus())) {
                StepError error = result.getError() != null
                        ? result.getError()
                        : StepError.of(FlowErrorCode.TF_AWAIT_INPUT, "等待人工输入");
                return Outcome.paused(steps, ctx, error, node.getId(),
                        RunExecutionState.PAUSE_REASON_AWAIT_INPUT, steps.size(),
                        currentId, incomingEdgeId);
            }

            if (RunStatus.FAILED.matches(result.getStatus())) {
                StepError error = result.getError() != null
                        ? result.getError()
                        : StepError.of(FlowErrorCode.TF_STEP_ERROR, "步骤失败");
                if (policy != null && policy.shouldPauseOnNodeFailure()) {
                    return Outcome.paused(steps, ctx, error, node.getId(),
                            RunExecutionState.PAUSE_REASON_NODE_FAILURE, steps.size(),
                            currentId, incomingEdgeId);
                }
                // 进入失败后继续；Outcome 仍带本步 error 作为 Run 主错误
                String fromId = currentId;
                String nextId;
                try {
                    nextId = walker.resolveNextNodeId(node, result);
                } catch (FlowExecutionException ex) {
                    return Outcome.failed(steps, ctx, error, steps.size());
                }
                String nextEdgeId = nextId != null ? walker.findEdgeId(fromId, nextId) : null;
                PostFailureState postState = new PostFailureState(stepIndex + 1, maxSteps);
                runPostFailureContinue(walker, ctx, registry, preExecuteHook, steps, onStep,
                        visitedNodeIds, postState, nextId, nextEdgeId);
                return Outcome.failed(steps, ctx, error, steps.size());
            }

            stepIndex++;
            String fromId = currentId;
            currentId = walker.resolveNextNodeId(node, result);
            incomingEdgeId = currentId != null ? walker.findEdgeId(fromId, currentId) : null;
        }

        return Outcome.passed(steps, ctx, steps.size());
    }

    /**
     * 失败后继续遍历。
     * alwaysRun 节点执行 Handler 并映射为 continued / continued_failed；
     * 其余节点合成 bypassed；未求值 condition 记 bypassed 后对各分支扇出；
     * 再次碰到已访问节点则结束该路径；本阶段不因节点失败或人工输入而暂停 Run。
     *
     * @param walker              图遍历器
     * @param ctx                 运行上下文
     * @param registry            Handler 注册表
     * @param preExecuteHook      执行前钩子（本阶段忽略其 pause/abort）
     * @param steps               步骤列表，本方法追加步骤
     * @param onStep              逐步回调，可为 null
     * @param visitedNodeIds      已访问节点集合，扇出与回边共享
     * @param state               共享步数计数
     * @param startNodeId         本路径起点节点 id
     * @param startIncomingEdgeId 进入起点的边 id
     */
    private void runPostFailureContinue(
            GraphWalker walker,
            FlowRunContext ctx,
            NodeHandlerRegistry registry,
            FlowNodePreExecuteHook preExecuteHook,
            List<StepResult> steps,
            Consumer<StepResult> onStep,
            Set<String> visitedNodeIds,
            PostFailureState state,
            String startNodeId,
            String startIncomingEdgeId
    ) {
        String currentId = startNodeId;
        String incomingEdgeId = startIncomingEdgeId;

        while (currentId != null) {
            if (visitedNodeIds.contains(currentId)) {
                break;
            }

            GraphNode node = walker.getNode(currentId);
            if (node == null) {
                // 图损坏：记继续失败步后结束本路径
                StepError missingErr = StepError.of(FlowErrorCode.TF_GRAPH_INVALID, "节点不存在: " + currentId);
                visitedNodeIds.add(currentId);
                recordStep(steps, StepResult.builder()
                        .nodeId(currentId)
                        .nodeName(currentId)
                        .edgeId(incomingEdgeId)
                        .status(RunStatus.CONTINUED_FAILED.getCode())
                        .durationMs(0L)
                        .error(missingErr)
                        .flowAfter(new HashMap<>(ctx.getFlow()))
                        .build(), onStep);
                break;
            }

            if (state.stepIndex > state.maxSteps) {
                // 超限：后续 alwaysRun 可能跑不到，记继续失败步后结束
                StepError limitError = stepLimitError(node, currentId, state.maxSteps);
                visitedNodeIds.add(currentId);
                recordStep(steps, errorStep(node, incomingEdgeId, ctx,
                        RunStatus.CONTINUED_FAILED.getCode(), limitError), onStep);
                break;
            }

            boolean alwaysRun = GraphNodeAlwaysRunSupport.isAlwaysRun(node);
            boolean isCondition = FlowNodeType.CONDITION.getCode().equals(node.getType());

            // 未求值 condition：落 bypassed，再对每个分支 target 递归失败后继续
            if (!alwaysRun && isCondition) {
                visitedNodeIds.add(currentId);
                recordStep(steps, syntheticStep(node, incomingEdgeId, ctx,
                        RunStatus.BYPASSED.getCode(), null, resolveDisplayName(node)), onStep);
                state.stepIndex++;
                for (String target : walker.listConditionBranchTargets(node)) {
                    if (visitedNodeIds.contains(target)) {
                        continue;
                    }
                    runPostFailureContinue(walker, ctx, registry, preExecuteHook, steps, onStep,
                            visitedNodeIds, state, target, walker.findEdgeId(currentId, target));
                }
                return;
            }

            visitedNodeIds.add(currentId);
            StepResult result;
            if (alwaysRun) {
                recordPreludeIgnoringAbort(preExecuteHook, node, steps, onStep);
                result = toPostFailureStep(registry.execute(ctx, node, incomingEdgeId));
            } else {
                result = syntheticStep(node, incomingEdgeId, ctx,
                        RunStatus.BYPASSED.getCode(), null, resolveDisplayName(node));
            }
            recordStep(steps, result, onStep);
            state.stepIndex++;

            String fromId = currentId;
            try {
                currentId = walker.resolveNextNodeId(node, result);
            } catch (FlowExecutionException ex) {
                // 例如 alwaysRun 的 condition 执行失败无 branchTaken：结束本路径
                break;
            }
            incomingEdgeId = currentId != null ? walker.findEdgeId(fromId, currentId) : null;
        }
    }

    /**
     * 失败后继续阶段写入执行前 prelude 步骤。
     * 不响应钩子的暂停或中止，避免失败后继续做到一半卡住。
     */
    private static void recordPreludeIgnoringAbort(
            FlowNodePreExecuteHook preExecuteHook,
            GraphNode node,
            List<StepResult> steps,
            Consumer<StepResult> onStep
    ) {
        if (preExecuteHook == null || preExecuteHook == FlowNodePreExecuteHook.NONE) {
            return;
        }
        FlowNodePreExecuteHook.PreExecuteOutcome pre = preExecuteHook.beforeNode(node);
        if (pre == null) {
            return;
        }
        for (StepResult prelude : pre.preludeSteps()) {
            recordStep(steps, prelude, onStep);
        }
    }

    /**
     * 将 alwaysRun 节点的 Handler 结果改写为失败后继续步骤态。
     * passed → continued；失败或暂停 → continued_failed（暂停改为失败语义，Run 不进入 paused）。
     *
     * @param result Handler 原始步骤结果
     * @return 改写 status 后的步骤结果
     */
    private static StepResult toPostFailureStep(StepResult result) {
        if (result == null) {
            return null;
        }
        if (RunStatus.PASSED.matches(result.getStatus())) {
            return result.toBuilder().status(RunStatus.CONTINUED.getCode()).build();
        }
        StepError err = result.getError();
        if (RunStatus.PAUSED.matches(result.getStatus())) {
            err = err != null ? err : StepError.of(FlowErrorCode.TF_AWAIT_INPUT, "失败后继续阶段不支持暂停");
        } else if (err == null) {
            err = StepError.of(FlowErrorCode.TF_STEP_ERROR, "失败后继续步骤失败");
        }
        return result.toBuilder()
                .status(RunStatus.CONTINUED_FAILED.getCode())
                .error(err)
                .build();
    }

    /**
     * 构造步数超限错误信息。
     *
     * @param node     即将执行但超限的节点
     * @param nodeId   节点 id
     * @param maxSteps 步数上限
     * @return 步骤错误
     */
    private static StepError stepLimitError(GraphNode node, String nodeId, int maxSteps) {
        String nodeName = resolveDisplayName(node);
        return StepError.of(FlowErrorCode.TF_RUN_STEP_LIMIT,
                "超过单次运行步数上限（" + maxSteps + "），停在节点「"
                        + nodeName + "」（" + nodeId + "），后续节点未执行");
    }

    /**
     * 合成一条带错误的步骤（不调用 Handler）。
     *
     * @param node           节点
     * @param incomingEdgeId 入边 id
     * @param ctx            运行上下文
     * @param status         步骤状态 code
     * @param error          错误信息
     * @return 步骤结果
     */
    private static StepResult errorStep(
            GraphNode node, String incomingEdgeId, FlowRunContext ctx, String status, StepError error) {
        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(node.getType())
                .nodeName(resolveDisplayName(node))
                .edgeId(incomingEdgeId)
                .status(status)
                .durationMs(0L)
                .error(error)
                .flowAfter(new HashMap<>(ctx.getFlow()))
                .build();
    }

    /** 追加步骤到列表；若有 onStep 则立刻回调 */
    private static void recordStep(List<StepResult> steps, StepResult result, Consumer<StepResult> onStep) {
        steps.add(result);
        if (onStep != null && result != null) {
            onStep.accept(result);
        }
    }

    /**
     * 不调用 Handler，合成一步（如 skipped、bypassed、续跑完成）。
     *
     * @param node           节点
     * @param incomingEdgeId 入边 id
     * @param ctx            运行上下文
     * @param status         步骤状态
     * @param assigns        赋值内容，可为 null
     * @param nodeName       展示名
     * @return 合成步骤
     */
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

    /** 节点展示名：优先 data.name，否则节点 id */
    private static String resolveDisplayName(GraphNode node) {
        if (node.getData() != null && node.getData().get("name") != null) {
            String name = String.valueOf(node.getData().get("name")).trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return node.getId();
    }

    /**
     * 失败后继续遍历的共享计数。
     * 扇出多条路径时共用同一实例，步数累计计入上限。
     */
    private static final class PostFailureState {
        /** 下一待执行步的序号（从 1 起算） */
        private int stepIndex;
        /** 单次 Run 步数上限 */
        private final int maxSteps;

        private PostFailureState(int stepIndex, int maxSteps) {
            this.stepIndex = stepIndex;
            this.maxSteps = maxSteps;
        }
    }

    /** 单次图执行结果：通过、失败或暂停 */
    @Getter
    public static class Outcome {
        /** 是否全部业务步骤通过 */
        private final boolean passed;
        /** 是否暂停等待续跑 */
        private final boolean paused;
        /** 已产生的全部步骤（含失败后继续） */
        private final List<StepResult> steps;
        /** 结束时的运行上下文 */
        private final FlowRunContext ctx;
        /** 失败或暂停时的主错误；失败后继续时为首个业务失败 */
        private final StepError error;
        /** 已执行步数 */
        private final int executedSteps;
        /** 暂停时所在节点 id */
        private final String pauseNodeId;
        /** 暂停原因 code */
        private final String pauseReason;
        /** 暂停时当前节点 id（续跑起点） */
        private final String currentNodeId;
        /** 暂停时进入当前节点的边 id */
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

        /** 构造通过结果 */
        public static Outcome passed(List<StepResult> steps, FlowRunContext ctx, int executedSteps) {
            return new Outcome(true, false, steps, ctx, null, executedSteps, null, null, null, null);
        }

        /** 构造失败结果；失败后继续结束后仍用此工厂，error 为首错 */
        public static Outcome failed(List<StepResult> steps, FlowRunContext ctx, StepError error, int executedSteps) {
            return new Outcome(false, false, steps, ctx, error, executedSteps, null, null, null, null);
        }

        /** 构造暂停结果，供续跑恢复 */
        public static Outcome paused(List<StepResult> steps, FlowRunContext ctx, StepError error,
                                       String pauseNodeId, String pauseReason, int executedSteps,
                                       String currentNodeId, String incomingEdgeId) {
            return new Outcome(false, true, steps, ctx, error, executedSteps, pauseNodeId, pauseReason,
                    currentNodeId, incomingEdgeId);
        }
    }
}
