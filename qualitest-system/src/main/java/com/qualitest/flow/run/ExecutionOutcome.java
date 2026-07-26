package com.qualitest.flow.run;

import com.qualitest.flow.exception.FlowErrorCode;
import lombok.Getter;

/**
 * 单次 execute 或 resume 调用结束后的结果摘要。
 * 供编排服务组装 API 响应，不直接落库。
 */
@Getter
public final class ExecutionOutcome {

    /** 结束时 Run 状态 */
    private final String terminalStatus;
    private final boolean passed;
    private final boolean paused;
    /** 对非 paused Run 重复 resume 时为 true */
    private final boolean idempotent;
    private final String errorCode;
    private final String errorMessage;
    /** 本次调用新执行的业务步数（不含审计步） */
    private final int executedSteps;

    private ExecutionOutcome(String terminalStatus, boolean passed, boolean paused, boolean idempotent,
                             String errorCode, String errorMessage, int executedSteps) {
        this.terminalStatus = terminalStatus;
        this.passed = passed;
        this.paused = paused;
        this.idempotent = idempotent;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.executedSteps = executedSteps;
    }

    public static ExecutionOutcome passed(int executedSteps) {
        return new ExecutionOutcome(RunStatus.PASSED, true, false, false, null, null, executedSteps);
    }

    public static ExecutionOutcome failed(String errorCode, String errorMessage, int executedSteps) {
        return new ExecutionOutcome(RunStatus.FAILED, false, false, false, errorCode, errorMessage, executedSteps);
    }

    public static ExecutionOutcome paused(String errorCode, String errorMessage, int executedSteps) {
        return new ExecutionOutcome(RunStatus.PAUSED, false, true, false, errorCode, errorMessage, executedSteps);
    }

    public static ExecutionOutcome aborted() {
        return new ExecutionOutcome(RunStatus.ABORTED, false, false, false, null, null, 0);
    }

    public boolean isAborted() {
        return RunStatus.ABORTED.equals(terminalStatus);
    }

    /**
     * 对非 paused 状态的 Run 再次 resume：不执行续跑。
     * 若当前已是 paused 则幂等且无错误码；否则带 TF_RUN_NOT_PAUSED。
     */
    public static ExecutionOutcome idempotent(String currentStatus) {
        boolean passed = RunStatus.PASSED.equals(currentStatus);
        boolean paused = RunStatus.PAUSED.equals(currentStatus);
        String errorCode = paused ? null : FlowErrorCode.TF_RUN_NOT_PAUSED.getCode();
        return new ExecutionOutcome(currentStatus, passed, paused, true, errorCode, null, 0);
    }
}
