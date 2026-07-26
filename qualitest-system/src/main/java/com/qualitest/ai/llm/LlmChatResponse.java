package com.qualitest.ai.llm;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 上游 LLM 单次调用的解析结果（应用层模型）。
 * <p>
 * 无论底层协议为何，统一暴露文本、工具调用、思考内容与 Token 用量。
 */
@Getter
@Builder
public class LlmChatResponse {

    /** 模型可见文本回复；存在 toolCalls 时可能为空 */
    private final String content;

    /** Extended Thinking 思考过程文本，仅部分模型与协议返回 */
    private final String thinkingContent;

    /**
     * 模型请求宿主执行的工具列表。
     * 非空时调用方应执行工具并将结果以 tool 消息追加后继续对话。
     */
    private final List<LlmToolCall> toolCalls;

    /** 上游结束原因，如 stop、tool_calls、end_turn、tool_use */
    private final String finishReason;

    /** 本次调用的 Token 用量统计 */
    private final LlmUsage usage;
}
