package com.qualitest.ai.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测 RoutingLlmProvider：按 LlmModelConfig.provider 把 chat/chatStream 分发到 OpenAI 或 Anthropic 实现。
 * 边界：Mock 两个 CompatibleProvider；不发真实 HTTP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=RoutingLlmProviderTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
     * 前提：provider=openai_compatible。
     * 期望：chat 只委托 OpenAiCompatibleProvider。
     */
    @Test
    @Order(1)
    @DisplayName("openai 协议委托 OpenAiCompatibleProvider")
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
     * 前提：provider=anthropic_compatible。
     * 期望：chat 只委托 AnthropicCompatibleProvider。
     */
    @Test
    @Order(2)
    @DisplayName("anthropic 协议委托 AnthropicCompatibleProvider")
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
     * 前提：provider 为 null。
     * 期望：默认走 OpenAiCompatibleProvider。
     */
    @Test
    @Order(3)
    @DisplayName("provider 为 null 时默认走 OpenAI")
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
     * 前提：provider 为未知标识 unknown_provider。
     * 期望：抛 LlmClientException，消息含「不支持的协议标识」。
     */
    @Test
    @Order(4)
    @DisplayName("未知 provider 抛 LlmClientException")
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
     * 前提：provider=anthropic_compatible，stream=true。
     * 期望：chatStream 委托 anthropicCompatibleProvider.chatStream。
     */
    @Test
    @Order(5)
    @DisplayName("流式 chatStream 委托 anthropic 实现")
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
