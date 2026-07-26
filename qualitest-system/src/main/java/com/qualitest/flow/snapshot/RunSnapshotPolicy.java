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
 * 控制：业务节点失败时是直接失败还是暂停等人决策；checkpoint 失败时是中止、暂停还是继续执行节点。
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
                .build();
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
