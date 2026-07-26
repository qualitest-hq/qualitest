package com.qualitest.ai.llm;

import org.springframework.stereotype.Component;

/**
 * Anthropic Messages API 协议 Provider。
 * <p>
 * 委托 {@link OkHttpAnthropicClient} 完成 HTTP 通信，请求路径为 {@code /v1/messages}。
 * 支持 Tool Use、Structured Outputs、Prompt Caching、Extended Thinking 与流式输出。
 */
@Component
public class AnthropicCompatibleProvider implements LlmProvider {

    private final OkHttpAnthropicClient okHttpAnthropicClient;

    public AnthropicCompatibleProvider(OkHttpAnthropicClient okHttpAnthropicClient) {
        this.okHttpAnthropicClient = okHttpAnthropicClient;
    }

    @Override
    public LlmChatResponse chat(LlmModelConfig model, LlmChatRequest request) {
        return okHttpAnthropicClient.chatSync(model, request);
    }

    @Override
    public void chatStream(LlmModelConfig model, LlmChatRequest request, LlmStreamCallback callback) {
        okHttpAnthropicClient.chatStream(model, request, callback);
    }
}
