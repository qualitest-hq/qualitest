package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * resumeRun API 响应。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeRunResult implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowRunId;

    /** 当前终态或 running */
    private String status;

    /** 非 paused 状态重复调用时为 true */
    private boolean idempotent;

    private String errorCode;

    private String errorMessage;
}
