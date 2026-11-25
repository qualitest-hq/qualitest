package com.qualitest.flow.snapshot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 单次 Run 的快照与步数策略。
 * <p>
 * 开跑时合成：{@code maxSteps} 来自入口 {@code data.maxSteps}（缺则默认）；
 * {@code onSnapshotFailure} 来自环境存档点插件 config（缺则 abort）。
 * 节点失败固定记失败并走失败后继续（{@code alwaysRun}）；暂停仅保留 Input {@code await_input}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunSnapshotPolicy {

    /** checkpoint 失败：中止 Run */
    public static final String ON_SNAPSHOT_FAILURE_ABORT = "abort";
    /** checkpoint 失败：记失败审计步后继续执行该节点业务逻辑 */
    public static final String ON_SNAPSHOT_FAILURE_CONTINUE = "continue";

    /** checkpoint 失败时的处理方式，默认 abort；仅 abort | continue */
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

    /**
     * 从入口步数上限与存档失败策略合成。
     *
     * @param entryMaxSteps     入口 {@code data.maxSteps}，可空（补默认）
     * @param onSnapshotFailure 存档点插件环境 config，可空（abort）；非法值（含历史 prompt）→ abort
     */
    public static RunSnapshotPolicy compose(Integer entryMaxSteps, String onSnapshotFailure) {
        return RunSnapshotPolicy.builder()
                .onSnapshotFailure(normalizeSnapshotFailure(onSnapshotFailure))
                .maxSteps(resolveMaxSteps(entryMaxSteps))
                .build();
    }

    /**
     * 把入口 / 配置里的步数上限转为策略用的整型。
     * 未配置用 {@link com.qualitest.flow.run.GraphWalker#DEFAULT_MAX_STEPS}；
     * 小于 1 归 1；大于 {@link com.qualitest.flow.run.GraphWalker#MAX_STEPS_CEILING} 则取封顶。
     *
     * @param configured 原始配置，可空
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

    /** checkpoint 失败时是否应中止 Run */
    public boolean shouldAbortOnSnapshotFailure() {
        return ON_SNAPSHOT_FAILURE_ABORT.equals(normalizedOnSnapshotFailure());
    }

    /** checkpoint 失败时是否应继续执行节点（仅记失败审计步） */
    public boolean shouldContinueOnSnapshotFailure() {
        return ON_SNAPSHOT_FAILURE_CONTINUE.equals(normalizedOnSnapshotFailure());
    }

    private String normalizedOnSnapshotFailure() {
        return normalizeSnapshotFailure(onSnapshotFailure);
    }

    /** 仅认 abort | continue；其余（含历史 prompt、空）→ abort */
    static String normalizeSnapshotFailure(String value) {
        String n = normalize(value, ON_SNAPSHOT_FAILURE_ABORT);
        if (ON_SNAPSHOT_FAILURE_CONTINUE.equals(n)) {
            return ON_SNAPSHOT_FAILURE_CONTINUE;
        }
        return ON_SNAPSHOT_FAILURE_ABORT;
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toLowerCase();
    }
}
