package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

/**
 * 套件运行项绑定正式 Run 参数
 *
 * @author qualitest
 * @since 2026-10-28
 */
@Getter
@Setter
public class BindTestSuiteRunItemParams {

    /** 关联正式 Run ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testFlowRunId;
}
