package com.qualitest.ai.tools;

/**
 * 造流 / MCP 工具归属类型。
 * <p>
 * 内置：平台枚举与 JSON 清单中的核心工具；
 * 插件：面向已启用平台插件的能力调用（不写画布），与内置 submit_* 分流。
 */
public enum FlowDesignToolKind {

    /** 平台内置工具（改图、只读勘察、跑流等） */
    BUILTIN,
    /** 插件能力工具（经 PluginInvoker 当场执行，不改图） */
    PLUGIN;

    /** 写入工具定义 JSON 的字段名 */
    public static final String JSON_FIELD = "toolKind";

    /** 小写 wire 名，供定义 JSON / 协议列表使用 */
    public String wire() {
        return name().toLowerCase();
    }
}
