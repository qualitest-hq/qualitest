package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 套件运行项写入终态参数
 *
 * @author qualitest
 * @since 2026-10-28
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinishTestSuiteRunItemParams {

    /** 项状态 */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 耗时毫秒 */
    private Long durationMs;
}
