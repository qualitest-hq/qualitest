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

    private String scenarioId;

    private String scenarioName;

    private Long testProjectEnvId;

    @Builder.Default
    private Map<String, Object> flowSeed = new HashMap<>();

    /** 节点失败策略，来自场景配置 */
    private String onNodeFailure;

    /** checkpoint 失败策略，来自场景配置 */
    private String onSnapshotFailure;
}
