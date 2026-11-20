package com.qualitest.flow.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 本次 Run 实际采用的场景与入参解析结果。
 * <p>
 * 从图 {@code meta.scenarios}（含 {@code inSeed}；旧图可回落 {@code inputPresets}）以及触发参数解析得到：
 * 环境 id、in 初值、失败策略等，供组装运行上下文、写 runConfig 首步、执行器决策使用。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedRunScenario {

    /** 命中的运行场景 id */
    private String scenarioId;

    /** 场景显示名称 */
    private String scenarioName;

    /** 本次 Run 使用的项目环境 id */
    private Long testProjectEnvId;

    /** 入参初值，Run 启动时写入 {@code ctx.in} */
    @Builder.Default
    private Map<String, Object> inSeed = new HashMap<>();

    /** 实际采用的入参预设 id；无预设时为 null */
    private String inputPresetId;

    /** 实际采用的入参预设显示名；无预设时为 null */
    private String inputPresetName;

    /** 节点失败策略（预设可覆盖场景） */
    private String onNodeFailure;

    /** checkpoint 失败策略（预设可覆盖场景） */
    private String onSnapshotFailure;

    /** 单次运行步数上限；空则用系统默认 */
    private Integer maxSteps;
}
