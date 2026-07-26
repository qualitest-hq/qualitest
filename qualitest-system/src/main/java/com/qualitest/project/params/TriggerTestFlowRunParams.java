package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST {@code /project/testFlowRun/trigger} 请求体。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerTestFlowRunParams {

    private Long testFlowId;

    /** 可选，覆盖场景内环境 */
    private Long testProjectEnvId;

    /** 可选，覆盖 meta.activeScenarioId */
    private String runScenarioId;

    /** manual / ci / schedule，默认 manual */
    private String triggerType;
}
