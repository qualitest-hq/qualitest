package com.qualitest.ai.llm;

/**
 * 大模型对话能力抽象。
 * <p>
 * 上层 Agent 与业务场景仅依赖本接口，不感知底层 HTTP 协议差异。
 */
public interface LlmProvider {

    /**
     * 同步发起一次对话请求，阻塞直到收到完整响应。
     *
     * @param model   已解析的模型连接配置
     * @param request 消息、工具与输出格式等参数
     * @return 解析后的模型响应
     */
    LlmChatResponse chat(LlmModelConfig model, LlmChatRequest request);

    /**
     * 流式发起对话请求，通过回调逐块接收增量内容。
     * <p>
     * 默认实现退化为同步 {@link #chat} 后一次性回调；具体协议实现可覆盖以支持 SSE。
     */
    default void chatStream(LlmModelConfig model, LlmChatRequest request, LlmStreamCallback callback) {
        LlmChatResponse response = chat(model, request);
        if (response.getContent() != null) {
            callback.onTextDelta(response.getContent());
        }
        if (response.getThinkingContent() != null) {
            callback.onThinkingDelta(response.getThinkingContent());
        }
        callback.onComplete(response);
    }
}
