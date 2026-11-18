package com.qualitest.plugin;

/**
 * 平台插件启用、登记或调用失败时的原因码；接口失败文案常带 code 前缀返回前端。
 */
public enum PluginErrorCode {

    /** 说明书缺失、格式或字段不合法 */
    MANIFEST_INVALID("MANIFEST_INVALID", "说明书无效"),
    /** 项目未配置插件所需挂载（如 bindAlias） */
    PROJECT_MOUNT_REQUIRED("PROJECT_MOUNT_REQUIRED", "缺少项目挂载"),
    /** 请求的能力名不在说明书 capabilities 中 */
    UNKNOWN_CAPABILITY("UNKNOWN_CAPABILITY", "未知能力"),
    /** 破坏性能力或启用需要总闸，但环境未开还原 */
    GATE_DENIED("GATE_DENIED", "总闸未开"),
    /** 环境未启用该插件，或环境本身不存在 */
    PROVIDER_NOT_FOUND("PROVIDER_NOT_FOUND", "插件未启用"),
    /** 环境配置 JSON 或环境记录无效 */
    CONFIG_SCHEMA_INVALID("CONFIG_SCHEMA_INVALID", "环境配置无效"),
    /** 本机启动不了 CLI、ping 失败，或 CLI 版本与登记版本不同 */
    AGENT_UNREACHABLE("AGENT_UNREACHABLE", "被测端 CLI 不可达或版本不匹配"),
    /** CLI 执行非 0、超时或结果不符合约定 */
    INVOKE_FAILED("INVOKE_FAILED", "插件调用失败"),
    /** 登记表中无此插件包 */
    PACKAGE_NOT_FOUND("PACKAGE_NOT_FOUND", "插件包未登记"),
    /** 说明书 requires 中的依赖插件尚未启用 */
    REQUIRES_UNSATISFIED("REQUIRES_UNSATISFIED", "依赖插件未启用"),
    /** 同环境另一插件已占用相同 ui-slot */
    ISOLATION_MUTEX("ISOLATION_MUTEX", "UI 槽位已被占用");

    private final String code;
    private final String defaultMessage;

    PluginErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /** 原因码字符串，接口失败文案常作前缀。 */
    public String getCode() {
        return code;
    }

    /** 默认中文说明。 */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
