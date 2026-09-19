package com.qualitest.project.params;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 触发正式 Run 的请求体。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerTestFlowRunParams {

    /** 要运行的测试流 id */
    private Long testFlowId;

    /** 可选，覆盖场景内绑定的环境 */
    private Long testProjectEnvId;

    /** 可选，覆盖图 meta 里的活动场景 */
    private String runScenarioId;

    /**
     * 触发来源，写入运行记录。
     * 常见值：manual（画布人手）、ai（全自动工具）、ci、schedule；空则按 manual。
     */
    private String triggerType;

    /**
     * 操作者用户 id，用于成员校验与后台跑流身份。
     * 未传时使用当前登录用户；无登录态时应显式传入。
     */
    private Long operatorUserId;
}
