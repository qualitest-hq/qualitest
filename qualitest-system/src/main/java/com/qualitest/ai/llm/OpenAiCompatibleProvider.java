package com.qualitest.ai.llm;

import org.springframework.stereotype.Component;

/**
 * Chat Completions 协议 Provider。
 * <p>
 * 委托 {@link OkHttpLlmClient} 完成 HTTP 通信，请求路径为 {@code /chat/completions}。
 */
@Component
public class OpenAiCompatibleProvider implements LlmProvider {

    private final OkHttpLlmClient okHttpLlmClient;

    public OpenAiCompatibleProvider(OkHttpLlmClient okHttpLlmClient) {
        this.okHttpLlmClient = okHttpLlmClient;
    }

    @Override
    public LlmChatResponse chat(LlmModelConfig model, LlmChatRequest request) {
        return okHttpLlmClient.chatSync(model, request);
    }

    @Override
    public void chatStream(LlmModelConfig model, LlmChatRequest request, LlmStreamCallback callback) {
        okHttpLlmClient.chatStream(model, request, callback);
    }
}
