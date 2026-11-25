package com.qualitest.flow.run;

import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;

import java.util.Collections;
import java.util.List;

/**
 * 流程图节点在业务 handler 执行前的扩展点。
 */
public interface FlowNodePreExecuteHook {

    FlowNodePreExecuteHook NONE = node -> PreExecuteOutcome.skip();

    PreExecuteOutcome beforeNode(GraphNode node);

    /**
     * 执行前钩子结果：可带审计 prelude；失败时仅 abort（不再 pause）。
     */
    record PreExecuteOutcome(List<StepResult> preludeSteps, boolean abort, StepError abortError) {

        public static PreExecuteOutcome skip() {
            return new PreExecuteOutcome(List.of(), false, null);
        }

        public static PreExecuteOutcome ok(List<StepResult> steps) {
            return new PreExecuteOutcome(steps != null ? List.copyOf(steps) : List.of(), false, null);
        }

        public static PreExecuteOutcome abort(StepError error, List<StepResult> steps) {
            return new PreExecuteOutcome(
                    steps != null ? List.copyOf(steps) : List.of(),
                    true,
                    error
            );
        }

        public List<StepResult> preludeSteps() {
            return preludeSteps != null ? preludeSteps : Collections.emptyList();
        }
    }
}
