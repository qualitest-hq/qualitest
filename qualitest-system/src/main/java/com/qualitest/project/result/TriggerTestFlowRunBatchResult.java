package com.qualitest.project.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量串行跑流的汇总结果。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerTestFlowRunBatchResult implements Serializable {

    @Builder.Default
    private List<TriggerTestFlowRunBatchItemResult> items = new ArrayList<>();

    /** 因非通过或触发失败提前结束剩余项 */
    private boolean stoppedEarly;

    private int passedCount;

    private int failedCount;

    private int pausedCount;

    private int otherCount;

    private int skippedCount;
}
