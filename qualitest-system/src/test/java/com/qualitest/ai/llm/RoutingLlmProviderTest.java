package com.qualitest.ai.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link RoutingLlmProvider} 单元测试：验证 LLM 协议路由器的委托逻辑。
 * <p>
 * 被测对象根据 {@link LlmModelConfig#getProvider()} 将 chat/chatStream 请求
 * 分发到 {@link OpenAiCompatibleProvider} 或 {@link AnthropicCompatibleProvider}；
 * provider 为 null 时默认走 OpenAI；未知 provider 抛 {@link LlmClientException}。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=RoutingLlmProviderTest
 */
class RoutingLlmProviderTest {

    private OpenAiCompatibleProvider openAiCompatibleProvider;
    private AnthropicCompatibleProvider anthropicCompatibleProvider;
    private RoutingLlmProvider routingLlmProvider;

    @BeforeEach
    void setUp() {
        openAiCompatibleProvider = mock(OpenAiCompatibleProvider.class);
        anthropicCompatibleProvider = mock(AnthropicCompatibleProvider.class);
        routingLlmProvider = new RoutingLlmProvider(openAiCompatibleProvider, anthropicCompatibleProvider);
    }

    /**
     * provider=openai_compatible 时 chat 应委托 OpenAiCompatibleProvider，不调用 Anthropic。
     */
    @Test
    void chat_openAiCompatibleProvider() {
        LlmModelConfig model = baseConfig(LlmProviderTypes.OPENAI_COMPATIBLE);
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hello")))
                .build();
        LlmChatResponse expected = LlmChatResponse.builder().content("ok").build();
        when(openAiCompatibleProvider.chat(model, request)).thenReturn(expected);

        LlmChatResponse actual = routingLlmProvider.chat(model, request);

        assertSame(expected, actual);
        verify(openAiCompatibleProvider).chat(model, request);
        verifyNoInteractions(anthropicCompatibleProvider);
    }

    /**
     * provider=anthropic_compatible 时 chat 应委托 AnthropicCompatibleProvider，不调用 OpenAI。
     */
    @Test
    void chat_anthropicCompatibleProvider() {
        LlmModelConfig model = baseConfig(LlmProviderTypes.ANTHROPIC_COMPATIBLE);
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hello")))
                .build();
        LlmChatResponse expected = LlmChatResponse.builder().content("ok").build();
        when(anthropicCompatibleProvider.chat(model, request)).thenReturn(expected);

        LlmChatResponse actual = routingLlmProvider.chat(model, request);

        assertSame(expected, actual);
        verify(anthropicCompatibleProvider).chat(model, request);
        verifyNoInteractions(openAiCompatibleProvider);
    }

    /**
     * provider 为 null 时应默认路由到 OpenAiCompatibleProvider（向后兼容）。
     */
    @Test
    void chat_nullProviderUsesOpenAi() {
        LlmModelConfig model = baseConfig(null);
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hello")))
                .build();
        when(openAiCompatibleProvider.chat(any(), any())).thenReturn(LlmChatResponse.builder().content("ok").build());

        routingLlmProvider.chat(model, request);

        verify(openAiCompatibleProvider).chat(model, request);
    }

    /**
     * provider 为未知标识（如 unknown_provider）时应拒绝调用。
     * 期望：抛 {@link LlmClientException}，消息含「不支持的协议标识」。
     */
    @Test
    void chat_unsupportedProviderThrows() {
        LlmModelConfig model = baseConfig("unknown_provider");
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hello")))
                .build();

        LlmClientException ex = assertThrows(LlmClientException.class,
                () -> routingLlmProvider.chat(model, request));
        assertTrue(ex.getMessage().contains("不支持的协议标识"));
    }

    /**
     * chatStream 应按 provider 路由；Anthropic provider 应调用 anthropicCompatibleProvider.chatStream。
     */
    @Test
    void chatStream_delegatesToAnthropicProvider() {
        LlmModelConfig model = baseConfig(LlmProviderTypes.ANTHROPIC_COMPATIBLE);
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hello")))
                .stream(true)
                .build();
        routingLlmProvider.chatStream(model, request, response -> {
        });
        verify(anthropicCompatibleProvider).chatStream(model, request, any(LlmStreamCallback.class));
    }

    private static LlmModelConfig baseConfig(String provider) {
        return LlmModelConfig.builder()
                .aiLlmModelId(1001L)
                .modelName("test-model")
                .provider(provider)
                .baseUrl("https://example.com")
                .apiKey("sk-test")
                .maxTokens(1024)
                .connectTimeoutMs(1000)
                .readTimeoutMs(1000)
                .writeTimeoutMs(1000)
                .build();
    }
}
