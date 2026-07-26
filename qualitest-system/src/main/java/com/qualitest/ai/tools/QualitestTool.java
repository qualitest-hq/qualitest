package com.qualitest.ai.tools;

import java.util.Map;

/**
 * Agent 可调用的只读工具契约。
 * <p>
 * 实现类由 {@link FlowDesignToolExecutor} 注册并按 {@link #getName()} 路由；
 * {@link #execute(Map, FlowDesignToolContext)} 返回 JSON 字符串作为 tool 消息 content。
 */
public interface QualitestTool {

    /** OpenAI tools 中的 function.name */
    String getName();

    /**
     * 执行工具逻辑。
     *
     * @param arguments 模型传入的参数（已解析为 Map）
     * @param context   项目、测试流、scope 等运行时上下文
     * @return JSON 字符串结果
     */
    String execute(Map<String, Object> arguments, FlowDesignToolContext context);
}
