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
 * 由图 meta.scenarios 与触发参数（scenarioId、testProjectEnvId 覆盖）解析得到，
 * 供上下文组装、runConfig 首步落库、以及快照失败策略使用。
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

    /** flow 变量初值，Run 启动时注入上下文 */
    @Builder.Default
    private Map<String, Object> flowSeed = new HashMap<>();

    /** 节点失败策略，来自场景配置 */
    private String onNodeFailure;

    /** checkpoint 失败策略，来自场景配置 */
    private String onSnapshotFailure;

    /** 单次运行步数上限，来自场景配置；空则用系统默认 */
    private Integer maxSteps;
}
