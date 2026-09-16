package com.qualitest.ai.llm;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 上游 LLM 单次调用的解析结果：正文、思考过程、工具调用、结束原因与 Token 用量。
 */
@Getter
@Builder(toBuilder = true)
public class LlmChatResponse {

    /** 模型可见文本回复；存在工具调用时可能为空 */
    private final String content;

    /** 模型思考/推理过程文本，仅部分模型返回；未开思考时应丢弃 */
    private final String thinkingContent;

    /**
     * 模型请求宿主执行的工具列表。
     * 非空时调用方应执行工具，将结果以 tool 消息追加后继续对话。
     */
    private final List<LlmToolCall> toolCalls;

    /** 上游结束原因，如 stop、tool_calls、end_turn、tool_use */
    private final String finishReason;

    /** 本次调用的 Token 用量 */
    private final LlmUsage usage;
}
