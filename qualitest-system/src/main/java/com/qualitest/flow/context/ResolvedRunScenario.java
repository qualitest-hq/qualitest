package com.qualitest.flow.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 本次 Run 实际使用的运行场景解析结果。
 * <p>
 * 自 scenario-entry 场景子集解析。
 * 供上下文组装（{@code inSeed} 灌 {@code ctx.in}）、runConfig 首步、快照失败策略使用。
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

    /** 解析所用的 scenario-entry 节点 id */
    private String entryNodeId;

    /** 本次 Run 使用的项目环境 id */
    private Long testProjectEnvId;

    /**
     * 入参值树，Run 启动时注入 {@code ctx.in}。
     */
    @Builder.Default
    private Map<String, Object> inSeed = new HashMap<>();

    /** 节点失败策略，来自场景配置 */
    private String onNodeFailure;

    /** checkpoint 失败策略，来自场景配置 */
    private String onSnapshotFailure;

    /** 单次运行步数上限，来自场景配置；空则用系统默认 */
    private Integer maxSteps;
}