package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 批量跑流队列中的单条：一条流 + 可选场景。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerTestFlowRunBatchItemParams {

    /** 测试流 id */
    private Long testFlowId;

    /** 可选，覆盖该流图 meta 中的活动场景；空则走后端默认解析 */
    private String runScenarioId;
}
