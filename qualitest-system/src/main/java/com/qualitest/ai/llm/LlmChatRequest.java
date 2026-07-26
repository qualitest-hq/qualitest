package com.qualitest.ai.llm;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 发往上游 LLM 的单次对话请求（应用层模型，非原始 HTTP JSON）。
 * <p>
 * 由 {@link LlmProvider} 实现类转换为各协议对应的 HTTP 请求体。
 */
@Getter
@Builder(toBuilder = true)
public class LlmChatRequest {

    /** 多轮消息上下文 */
    private final List<LlmMessage> messages;

    /**
     * 工具定义列表。
     * 每项为 {@code {type: function, function: {name, description, parameters}}} 结构，
     * Anthropic 客户端会提取为 {@code input_schema} 形式。
     */
    private final List<Map<String, Object>> tools;

    /**
     * 工具调用策略。
     * 取值：{@code auto}、{@code none}、{@code any}、{@code required}，或具体工具名。
     */
    private final String toolChoice;

    /**
     * 响应格式约束。
     * <ul>
     *   <li>{@code {type: json_object}} — 强制 JSON 对象输出</li>
     *   <li>{@code {type: json_schema, schema: {...}}} — 按 JSON Schema 约束输出</li>
     * </ul>
     */
    private final Map<String, Object> responseFormat;

    /** 是否启用 SSE 流式响应 */
    @Builder.Default
    private final boolean stream = false;

    /** 是否对 system 提示启用 Prompt Caching（Anthropic） */
    @Builder.Default
    private final boolean promptCaching = false;

    /** 是否启用 Extended Thinking 扩展思考（Anthropic） */
    @Builder.Default
    private final boolean extendedThinking = false;

    /** 是否启用 OpenAI reasoning（reasoning_effort） */
    @Builder.Default
    private final boolean reasoningEnabled = false;

    /** Extended Thinking 的 token 预算上限 */
    private final Integer thinkingBudgetTokens;
}
