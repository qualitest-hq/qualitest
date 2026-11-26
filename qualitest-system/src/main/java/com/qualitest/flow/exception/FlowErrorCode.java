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
    /** HTTP 已是 2xx，但响应体业务码不在成功白名单内；步骤报告写入 http.bizCheck */
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
    /** 快照插件未就绪（未启用、未挂载等），或执行 checkpoint 时环境对象为空 */
    TF_SNAPSHOT_ENDPOINT("TF_SNAPSHOT_ENDPOINT", "reset 端点未配置"),
    /** 环境已允许还原，但插件 archive.create 失败 */
    TF_SNAPSHOT_FAILED("TF_SNAPSHOT_FAILED", "快照失败"),
    /** 环境已允许还原，但插件 archive.restore 失败 */
    TF_SNAPSHOT_RESTORE_FAILED("TF_SNAPSHOT_RESTORE_FAILED", "还原失败"),
    /** 对非 paused 的 Run 重复调用 resume */
    TF_RUN_NOT_PAUSED("TF_RUN_NOT_PAUSED", "运行未处于暂停状态"),
    /** resume 参数无效、快照不在栈、用户主动中止等 */
    TF_RUN_RESUME_INVALID("TF_RUN_RESUME_INVALID", "恢复决策无效"),
    /** Input 节点到达：Run 进入 paused，等待人工填写（非失败） */
    TF_AWAIT_INPUT("TF_AWAIT_INPUT", "等待人工输入"),
    /** 人工提交的 inputs 未通过字段类型 / 必填 / options 校验 */
    TF_INPUT_INVALID("TF_INPUT_INVALID", "人工输入校验失败"),
    /**
     * 整次 Run 内第二次（及以后）对 http-return / subflow-return 发起交付。
     * 该返回步骤记失败；若此前已交付成功则 delivered 保持不变。
     */
    TF_RETURN_DUPLICATE("TF_RETURN_DUPLICATE", "返回节点执行超过一次"),
    /**
     * 开放 HTTP 或子流路径走尽仍未执行对应返回节点。
     * 收束已用入口 fallbackOut（或平台默认包）补交；Outcome 仍记失败；
     * 调用方或父流仍可从已写出/已挂载的 out 读到兜底内容。
     */
    TF_RETURN_MISSING("TF_RETURN_MISSING", "未执行到返回节点"),
    /**
     * 开放 HTTP 已定稿并尝试写出，但握连接写出失败（如调用方已断开）。
     * 图与报告仍跑完；Run 记失败，不因后续断言通过而显示绿灯。
     */
    TF_DELIVERY_WRITE_FAILED("TF_DELIVERY_WRITE_FAILED", "开放响应写出失败"),
    /** pluginAction 所需插件未启用、未登记，或插件未提供所配置的 capability */
    TF_NODE_CAPABILITY_UNAVAILABLE("TF_NODE_CAPABILITY_UNAVAILABLE", "插件能力不可用"),
    /** pluginAction 调用插件失败（插件返回非成功或调用过程异常） */
    TF_PLUGIN_ACTION_FAILED("TF_PLUGIN_ACTION_FAILED", "插件步骤执行失败");

    /** 错误码字符串 */
    private final String code;
    /** 默认错误提示 */
    private final String defaultMessage;

    /** 构造错误码枚举项 */
    FlowErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /** 获取错误码字符串 */
    public String getCode() {
        return code;
    }

    /** 获取默认错误提示 */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
