package com.qualitest.flow.snapshot;

import com.qualitest.flow.context.ResolvedRunScenario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 单次 Run 的快照与失败处理策略。
 * <p>
 * 来自流程图 meta.scenarios 里当前运行场景的配置，在 Run 启动时解析。
 * 控制：业务节点失败时是直接失败还是暂停等人决策；checkpoint 失败时是中止、暂停还是继续执行节点；
 * 以及单次运行的步数上限。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunSnapshotPolicy {

    /** 业务节点失败：直接标记 Run 失败 */
    public static final String ON_NODE_FAILURE_FAIL = "fail";
    /** 业务节点失败：暂停 Run，等待用户 resume */
    public static final String ON_NODE_FAILURE_PROMPT = "prompt";

    /** checkpoint 失败：中止 Run */
    public static final String ON_SNAPSHOT_FAILURE_ABORT = "abort";
    /** checkpoint 失败：记失败审计步后继续执行该节点业务逻辑 */
    public static final String ON_SNAPSHOT_FAILURE_CONTINUE = "continue";
    /** checkpoint 失败：暂停 Run，等待用户 resume */
    public static final String ON_SNAPSHOT_FAILURE_PROMPT = "prompt";

    /** 业务节点失败时的处理方式，默认 fail */
    @Builder.Default
    private String onNodeFailure = ON_NODE_FAILURE_FAIL;

    /** checkpoint 失败时的处理方式，默认 abort */
    @Builder.Default
    private String onSnapshotFailure = ON_SNAPSHOT_FAILURE_ABORT;

    /**
     * 单次运行允许执行的最大步数（含即将执行的节点计一步）。
     * 未配置时用 {@link com.qualitest.flow.run.GraphWalker#DEFAULT_MAX_STEPS}；
     * 配置值经 {@link #resolveMaxSteps(Integer)} 裁剪到合法范围。
     */
    @Builder.Default
    private int maxSteps = com.qualitest.flow.run.GraphWalker.DEFAULT_MAX_STEPS;

    public static RunSnapshotPolicy defaults() {
        return RunSnapshotPolicy.builder().build();
    }

    /** 从已解析的运行场景构建策略；场景为空时用默认值 */
    public static RunSnapshotPolicy fromScenario(ResolvedRunScenario scenario) {
        if (scenario == null) {
            return defaults();
        }
        return RunSnapshotPolicy.builder()
                .onNodeFailure(normalize(scenario.getOnNodeFailure(), ON_NODE_FAILURE_FAIL))
                .onSnapshotFailure(normalize(scenario.getOnSnapshotFailure(), ON_SNAPSHOT_FAILURE_ABORT))
                .maxSteps(resolveMaxSteps(scenario.getMaxSteps()))
                .build();
    }

    /**
     * 把场景里的步数上限转为策略用的整型。
     * 未配置用 {@link com.qualitest.flow.run.GraphWalker#DEFAULT_MAX_STEPS}；
     * 小于 1 归 1；大于 {@link com.qualitest.flow.run.GraphWalker#MAX_STEPS_CEILING} 则取封顶。
     *
     * @param configured 场景原始配置，可空
     * @return 裁剪后的步数上限
     */
    public static int resolveMaxSteps(Integer configured) {
        if (configured == null) {
            return com.qualitest.flow.run.GraphWalker.DEFAULT_MAX_STEPS;
        }
        int value = configured;
        if (value < 1) {
            return 1;
        }
        if (value > com.qualitest.flow.run.GraphWalker.MAX_STEPS_CEILING) {
            return com.qualitest.flow.run.GraphWalker.MAX_STEPS_CEILING;
        }
        return value;
    }

    /** 业务节点失败时是否应暂停 Run */
    public boolean shouldPauseOnNodeFailure() {
        return ON_NODE_FAILURE_PROMPT.equals(normalizedOnNodeFailure());
    }

    /** checkpoint 失败时是否应中止 Run */
    public boolean shouldAbortOnSnapshotFailure() {
        return ON_SNAPSHOT_FAILURE_ABORT.equals(normalizedOnSnapshotFailure());
    }

    /** checkpoint 失败时是否应暂停 Run */
    public boolean shouldPauseOnSnapshotFailure() {
        return ON_SNAPSHOT_FAILURE_PROMPT.equals(normalizedOnSnapshotFailure());
    }

    /** checkpoint 失败时是否应继续执行节点（仅记失败审计步） */
    public boolean shouldContinueOnSnapshotFailure() {
        return ON_SNAPSHOT_FAILURE_CONTINUE.equals(normalizedOnSnapshotFailure());
    }

    private String normalizedOnNodeFailure() {
        return normalize(onNodeFailure, ON_NODE_FAILURE_FAIL);
    }

    private String normalizedOnSnapshotFailure() {
        return normalize(onSnapshotFailure, ON_SNAPSHOT_FAILURE_ABORT);
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toLowerCase();
    }
}
