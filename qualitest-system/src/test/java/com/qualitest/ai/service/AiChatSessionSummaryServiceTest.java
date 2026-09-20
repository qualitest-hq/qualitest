package com.qualitest.ai.service;

import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.llm.LlmMessage;
import com.qualitest.ai.llm.LlmModelConfig;
import com.qualitest.ai.llm.history.HistoryWindowPolicy;
import com.qualitest.ai.llm.history.HistoryWindowPolicyResolver;
import com.qualitest.ai.llm.lc4j.Lc4jClientFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 会话摘要服务单测。
 * 覆盖：助手条数不足跳过；达条件时用被裁历史调用模型并写回摘要。
 * 全 Mock，不访问真实大模型与数据库。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiChatSessionSummaryServiceTest {

    private IAiChatSessionService aiChatSessionService;
    private AiChatConversationService conversationService;
    private IAiLlmModelService modelService;
    private Lc4jClientFactory lc4jClientFactory;
    private ChatModel chatModel;
    private HistoryWindowPolicyResolver policyResolver;
    private ThreadPoolTaskExecutor executor;
    private AiChatSessionSummaryService summaryService;

    @BeforeEach
    void setUp() {
        aiChatSessionService = mock(IAiChatSessionService.class);
        conversationService = mock(AiChatConversationService.class);
        modelService = mock(IAiLlmModelService.class);
        lc4jClientFactory = mock(Lc4jClientFactory.class);
        chatModel = mock(ChatModel.class);
        policyResolver = mock(HistoryWindowPolicyResolver.class);
        executor = new ThreadPoolTaskExecutor();
        executor.initialize();
        when(lc4jClientFactory.chatModel(any(), anyBoolean())).thenReturn(chatModel);
        summaryService = new AiChatSessionSummaryService(
                aiChatSessionService, conversationService, modelService,
                lc4jClientFactory, policyResolver, executor);
    }

    /**
     * 前提：会话存在且 assistant 消息数低于触发阈值。
     * 期望：不调用 LLM，不写 context_summary。
     */
    @Test
    @Order(1)
    @DisplayName("assistant 条数不足时跳过摘要")
    void refreshSummaryIfNeeded_skipsWhenAssistantCountLow() throws Exception {
        AiChatSession session = AiChatSession.builder()
                .aiChatSessionId(1L)
                .delStatus(0)
                .build();
        when(aiChatSessionService.selectAiChatSessionById(1L)).thenReturn(session);
        when(conversationService.countAssistantMessages(1L)).thenReturn(1);

        summaryService.refreshSummaryIfNeeded(1L, 100L);

        verify(chatModel, never()).chat(any(ChatRequest.class));
    }

    /**
     * 前提：assistant 轮次达阈值，模型与历史窗口策略可用。
     * 期望：加载被裁历史调 LLM 生成摘要，并更新 context_summary 与 summaryMessageCount。
     */
    @Test
    @Order(2)
    @DisplayName("达阈值时调 LLM 写回摘要")
    void refreshSummaryIfNeeded_updatesSummaryWhenTriggered() throws Exception {
        AiChatSession session = AiChatSession.builder()
                .aiChatSessionId(1L)
                .delStatus(0)
                .summaryMessageCount(0)
                .build();
        LlmModelConfig modelConfig = LlmModelConfig.builder()
                .aiLlmModelId(100L)
                .modelName("gpt-test")
                .provider("openai_compatible")
                .baseUrl("https://example.com/v1")
                .apiKey("sk-test")
                .build();
        HistoryWindowPolicy policy = HistoryWindowPolicy.builder().countLimit(2).tokenBudget(12000).build();

        when(aiChatSessionService.selectAiChatSessionById(1L)).thenReturn(session);
        when(conversationService.countAssistantMessages(1L)).thenReturn(5);
        when(conversationService.countSessionMessages(1L)).thenReturn(10);
        when(modelService.resolve(100L)).thenReturn(modelConfig);
        when(policyResolver.resolve(modelConfig)).thenReturn(policy);
        when(conversationService.loadDroppedHistoryForSummary(eq(1L), eq(modelConfig), eq(policy), anyInt()))
                .thenReturn(List.of(LlmMessage.user("early question"), LlmMessage.assistant("early answer", null)));
        when(conversationService.formatMessagesForSummary(any())).thenReturn("user: early\nassistant: early answer");
        when(conversationService.loadMessagesForLlm(eq(1L), eq(modelConfig), eq(policy), anyInt()))
                .thenReturn(List.of(LlmMessage.user("recent")));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("用户早期确认登录节点"))
                .build());

        summaryService.refreshSummaryIfNeeded(1L, 100L);

        verify(conversationService).updateContextSummary(eq(1L), eq("用户早期确认登录节点"), eq(9));
    }
}
