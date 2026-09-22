package com.qualitest.flow.run;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 测试流 Run / 步骤状态枚举。
 * <p>
 * code 为 {@code test_flow_run.status}、{@code test_flow_run_step.status} 及 API JSON 持久化值。
 */
@Getter
public enum RunStatus {

    /** 执行中 */
    RUNNING("running"),
    /** 全部步骤通过 / 步骤成功 */
    PASSED("passed"),
    /** 失败 */
    FAILED("failed"),
    /** 等待用户 resume / 步骤主动暂停 */
    PAUSED("paused"),
    /** 用户中止或超时中止 */
    ABORTED("aborted"),
    /** 用户取消 */
    CANCELLED("cancelled"),
    /** 步骤未执行（跳过） */
    SKIPPED("skipped");

    private static final Map<String, RunStatus> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(RunStatus::getCode, s -> s));

    private static final Set<String> TERMINAL_CODES = Set.of(
            PASSED.code, FAILED.code, PAUSED.code, ABORTED.code, CANCELLED.code);

    /** 允许导出简易 HTML 报告的运行状态：成功、失败、中止、取消 */
    private static final Set<String> REPORT_EXPORTABLE_CODES = Set.of(
            PASSED.code, FAILED.code, ABORTED.code, CANCELLED.code);

    private final String code;

    RunStatus(String code) {
        this.code = code;
    }

    /** 判断是否与给定持久化字符串相同 */
    public boolean matches(String status) {
        return code.equals(status);
    }

    /** 由持久化 code 解析枚举；未知或空串返回 empty */
    public static Optional<RunStatus> fromCode(String status) {
        if (status == null || status.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(status));
    }

    /** 是否为已知状态 code */
    public static boolean isKnown(String status) {
        return status != null && BY_CODE.containsKey(status);
    }

    /**
     * 是否为 Run 终态（含 paused：等待 resume，不再处于 running）。
     */
    public static boolean isTerminal(String status) {
        return status != null && TERMINAL_CODES.contains(status);
    }

    /**
     * 是否允许导出简易 HTML 运行报告。
     * 允许：passed、failed、aborted、cancelled；不允许：running、paused。
     */
    public static boolean isReportExportable(String status) {
        return status != null && REPORT_EXPORTABLE_CODES.contains(status);
    }

    /** 是否为步骤可用状态 */
    public boolean isStepStatus() {
        return this == PASSED || this == FAILED || this == PAUSED || this == SKIPPED;
    }
}
