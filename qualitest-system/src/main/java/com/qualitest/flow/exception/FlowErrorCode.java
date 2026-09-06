package com.qualitest.flow.exception;

/**
 * 测试流执行错误码（TF_*）
 */
public enum FlowErrorCode {

    TF_PLACEHOLDER_UNDEFINED("TF_PLACEHOLDER_UNDEFINED", "占位符无法解析"),
    TF_HTTP_UNBOUND("TF_HTTP_UNBOUND", "未绑定 testProjectApiId"),
    TF_HTTP_CALLMODE("TF_HTTP_CALLMODE", "HTTP 节点 callMode 无效或缺失"),
    TF_HTTP_EXTERNAL_DENIED("TF_HTTP_EXTERNAL_DENIED", "外联 URL 被拒绝"),
    /** subflowId 无效、引用非子流定义、跨项目引用等 */
    TF_SUBFLOW_INVALID("TF_SUBFLOW_INVALID", "子流引用无效"),
    /** 子图内出现 subflow 节点 */
    TF_SUBFLOW_NESTED("TF_SUBFLOW_NESTED", "子流不可嵌套"),
    TF_HTTP_STATUS("TF_HTTP_STATUS", "HTTP 状态码非成功"),
    /** HTTP 已是 2xx，但响应体业务码不在成功白名单内（步骤报告见 http.bizCheck） */
    TF_BIZ_CODE("TF_BIZ_CODE", "业务响应码表示失败"),
    TF_HTTP_TIMEOUT("TF_HTTP_TIMEOUT", "HTTP 请求超时"),
    TF_ASSERT_FAILED("TF_ASSERT_FAILED", "断言未通过"),
    TF_STEP_ERROR("TF_STEP_ERROR", "步骤执行异常"),
    TF_START_NODE("TF_START_NODE", "开始节点无效"),
    TF_RUN_STEP_LIMIT("TF_RUN_STEP_LIMIT", "超过步数上限"),
    TF_GRAPH_INVALID("TF_GRAPH_INVALID", "流程图校验失败"),
    TF_NODE_UNSUPPORTED("TF_NODE_UNSUPPORTED", "不支持的节点类型"),
    TF_BRANCH_UNWIRED("TF_BRANCH_UNWIRED", "条件分支未连线"),
    TF_SCRIPT_TIMEOUT("TF_SCRIPT_TIMEOUT", "脚本执行超时"),
    TF_SCRIPT_ERROR("TF_SCRIPT_ERROR", "脚本执行失败"),
    /** envUrl 无法派生 test-support 地址，或执行 checkpoint 时环境对象为空 */
    TF_SNAPSHOT_ENDPOINT("TF_SNAPSHOT_ENDPOINT", "reset 端点未配置"),
    /** 环境已允许还原，但调用被测 snapshot 接口失败 */
    TF_SNAPSHOT_FAILED("TF_SNAPSHOT_FAILED", "快照失败"),
    /** 环境已允许还原，但调用被测 restore 接口失败 */
    TF_SNAPSHOT_RESTORE_FAILED("TF_SNAPSHOT_RESTORE_FAILED", "还原失败"),
    /** 对非 paused 的 Run 重复调用 resume */
    TF_RUN_NOT_PAUSED("TF_RUN_NOT_PAUSED", "运行未处于暂停状态"),
    /** resume 参数无效、快照不在栈、用户主动中止等 */
    TF_RUN_RESUME_INVALID("TF_RUN_RESUME_INVALID", "恢复决策无效"),
    /** Input 节点到达：Run 进入 paused，等待人工填写（非失败） */
    TF_AWAIT_INPUT("TF_AWAIT_INPUT", "等待人工输入"),
    /** 人工提交的 inputs 未通过字段类型 / 必填 / options 校验 */
    TF_INPUT_INVALID("TF_INPUT_INVALID", "人工输入校验失败");

    private final String code;
    private final String defaultMessage;

    FlowErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
