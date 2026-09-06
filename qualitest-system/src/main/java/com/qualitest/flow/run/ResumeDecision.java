package com.qualitest.flow.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * paused Run 的续跑决策（引擎内部模型，由 API 参数映射而来）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeDecision {

    /** 还原到指定快照后再从该节点重试 */
    public static final String RESTORE_AND_RETRY = "restoreAndRetry";
    /** 不还原，从暂停节点原地重试 */
    public static final String RETRY_IN_PLACE = "retryInPlace";
    /** 跳过暂停节点，沿出边继续 */
    public static final String SKIP = "skip";
    /** 中止本次 Run */
    public static final String ABORT = "abort";
    /** 提交人工输入后完成 Input 节点并继续（仅 await_input） */
    public static final String CONTINUE_WITH_INPUT = "continueWithInput";

    /** 决策字符串，见上方常量 */
    private String decision;

    /**
     * restoreAndRetry 指定的快照 id。
     * 可空：引擎按暂停节点或快照栈顶回退。
     */
    private String snapshotId;

    /**
     * continueWithInput 时客户端提交的字段值：字段 name → 值
     * （string / number / boolean / 数组）。
     */
    private java.util.Map<String, Object> inputs;

    /**
     * continueWithInput 校验写入 flow 后的 assigns 明细。
     * 由执行器在调用续跑规划前填入，不来自 API。
     */
    private Object completionAssigns;

    /** 操作者用户名，写入 resume 决策审计步 */
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

    public boolean isContinueWithInput() {
        return CONTINUE_WITH_INPUT.equalsIgnoreCase(decision);
    }
}
