package com.qualitest.ai.llm;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * LLM 协议路由入口（Spring 主 {@link LlmProvider} Bean）。
 * <p>
 * 读取 {@link LlmModelConfig#getProvider()}，将请求分发给对应的协议实现类。
 */
@Component
@Primary
public class RoutingLlmProvider implements LlmProvider {

    private final OpenAiCompatibleProvider openAiCompatibleProvider;
    private final AnthropicCompatibleProvider anthropicCompatibleProvider;

    public RoutingLlmProvider(OpenAiCompatibleProvider openAiCompatibleProvider,
                              AnthropicCompatibleProvider anthropicCompatibleProvider) {
        this.openAiCompatibleProvider = openAiCompatibleProvider;
        this.anthropicCompatibleProvider = anthropicCompatibleProvider;
    }

    @Override
    public LlmChatResponse chat(LlmModelConfig model, LlmChatRequest request) {
        return delegate(model).chat(model, request);
    }

    @Override
    public void chatStream(LlmModelConfig model, LlmChatRequest request, LlmStreamCallback callback) {
        delegate(model).chatStream(model, request, callback);
    }

    /** 按协议标识选择底层 Provider；不支持的协议抛出 {@link LlmClientException} */
    private LlmProvider delegate(LlmModelConfig model) {
        if (LlmProviderTypes.isAnthropic(model.getProvider())) {
            return anthropicCompatibleProvider;
        }
        if (LlmProviderTypes.isOpenAiCompatible(model.getProvider())) {
            return openAiCompatibleProvider;
        }
        throw new LlmClientException("不支持的协议标识: " + model.getProvider());
    }
}
