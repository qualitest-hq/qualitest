package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 批量跑流中单条的结果。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerTestFlowRunBatchItemResult implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowId;

    /** 实际使用的场景 id（可能为空） */
    private String runScenarioId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long runId;

    /** 终态或触发失败时的占位状态 */
    private String status;

    private String errorMessage;

    /** 运行耗时毫秒；未跑完或触发失败时可能为空 */
    private Long durationMs;
}
