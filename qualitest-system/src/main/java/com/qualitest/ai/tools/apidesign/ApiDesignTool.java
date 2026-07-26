package com.qualitest.ai.tools.apidesign;

import java.util.Map;

/**
 * AI API 助手单个 Function Calling 工具的执行接口。
 */
public interface ApiDesignTool {

    /** 工具在 Function Calling 中的名称 */
    String getName();

    /**
     * 执行工具。
     *
     * @param arguments 模型传入的参数（已解析为 Map）
     * @param context   当前项目/API/脚本草稿等上下文
     * @return JSON 字符串结果（含 error 字段表示失败）
     */
    String execute(Map<String, Object> arguments, ApiDesignToolContext context);
}
