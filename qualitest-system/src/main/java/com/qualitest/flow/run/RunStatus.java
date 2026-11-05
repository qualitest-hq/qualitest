package com.qualitest.flow.run;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 测试流 Run 与单步状态。
 * <p>
 * code 写入运行表、步骤表及接口 JSON。
 * 失败后继续专用步骤态：bypassed（路过未跑）、continued（仍执行且成功）、continued_failed（仍执行但本步失败）；
 * 人工续跑跳过仍用 skipped，与 bypassed 含义不同。
 */
@Getter
public enum RunStatus {

    /**
     * Run 执行中
     */
    RUNNING("running"),
    /**
     * Run 全部通过，或步骤 Handler 执行成功
     */
    PASSED("passed"),
    /**
     * Run 失败，或业务步骤执行失败（失败后继续的首错）
     */
    FAILED("failed"),
    /**
     * Run 或步骤暂停，等待用户续跑或输入
     */
    PAUSED("paused"),
    /**
     * Run 被中止
     */
    ABORTED("aborted"),
    /**
     * Run 被取消
     */
    CANCELLED("cancelled"),
    /**
     * 步骤未执行：人工跳过或续跑 skip
     */
    SKIPPED("skipped"),
    /**
     * 失败后继续阶段路过本节点且未执行 Handler（含未求值的 condition）
     */
    BYPASSED("bypassed"),
    /**
     * 失败后继续阶段执行 alwaysRun 节点且本步成功
     */
    CONTINUED("continued"),
    /**
     * 失败后继续阶段执行 alwaysRun 节点但本步失败；不替换 Run 主错误，后续 alwaysRun 仍继续
     */
    CONTINUED_FAILED("continued_failed");

    private static final Map<String, RunStatus> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(RunStatus::getCode, s -> s));

    private static final Set<String> TERMINAL_CODES = Set.of(
            PASSED.code, FAILED.code, PAUSED.code, ABORTED.code, CANCELLED.code);

    /**
     * 允许导出简易 HTML 报告的运行状态：成功、失败、中止、取消
     */
    private static final Set<String> REPORT_EXPORTABLE_CODES = Set.of(
            PASSED.code, FAILED.code, ABORTED.code, CANCELLED.code);

    private final String code;

    RunStatus(String code) {
        this.code = code;
    }

    /**
     * 判断是否与给定持久化字符串相同
     */
    public boolean matches(String status) {
        return code.equals(status);
    }

    /**
     * 由持久化 code 解析枚举；未知或空串返回 empty
     */
    public static Optional<RunStatus> fromCode(String status) {
        if (status == null || status.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE.get(status));
    }

    /**
     * 是否为已知状态 code
     */
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

    /**
     * 是否可作为步骤 status 落库。
     * 含普通步骤态与失败后继续三态。
     */
    public boolean isStepStatus() {
        return this == PASSED || this == FAILED || this == PAUSED || this == SKIPPED
                || this == BYPASSED || this == CONTINUED || this == CONTINUED_FAILED;
    }

    /**
     * 是否应按失败对待（进失败摘要、默认展开失败卡片等）。
     *
     * @param status 步骤 status 字符串
     * @return failed 或 continued_failed 时为 true
     */
    public static boolean isFailureLike(String status) {
        return FAILED.matches(status) || CONTINUED_FAILED.matches(status);
    }
}
