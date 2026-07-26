package com.qualitest.flow.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 用户对 paused Run 提交的续跑决策（引擎内部模型）。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeDecision {

    /** 先还原到指定快照，再从该节点重试 */
    public static final String RESTORE_AND_RETRY = "restoreAndRetry";
    /** 不还原，从暂停节点原地重试 */
    public static final String RETRY_IN_PLACE = "retryInPlace";
    /** 跳过暂停节点，沿出边继续 */
    public static final String SKIP = "skip";
    /** 中止 Run */
    public static final String ABORT = "abort";

    private String decision;

    /** restoreAndRetry 时指定要还原的 snapshotId；可空，由引擎按暂停节点回退 */
    private String snapshotId;

    /** 操作者用户名，写入决策审计步 */
    private String operator;

    public boolean isAbort() {
        return ABORT.equalsIgnoreCase(decision);
    }

    public boolean isRestoreAndRetry() {
        return RESTORE_AND_RETRY.equalsIgnoreCase(decision);
    }

    public boolean isRetryInPlace() {
        return RETRY_IN_PLACE.equalsIgnoreCase(decision);
    }

    public boolean isSkip() {
        return SKIP.equalsIgnoreCase(decision);
    }
}
