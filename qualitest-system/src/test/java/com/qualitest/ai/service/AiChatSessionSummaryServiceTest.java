package com.qualitest.ai.service;

import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.llm.LlmChatResponse;
import com.qualitest.ai.llm.LlmMessage;
import com.qualitest.ai.llm.LlmModelConfig;
import com.qualitest.ai.llm.LlmProvider;
import com.qualitest.ai.llm.history.HistoryWindowPolicy;
import com.qualitest.ai.llm.history.HistoryWindowPolicyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测 AiChatSessionSummaryService：assistant 条数不足跳过；达条件时用被裁历史调 LLM 写回摘要。
 * 边界：全 Mock，不调用真实 LLM / DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AiChatSessionSummaryServiceTest
 */
class AiChatSessionSummaryServiceTest {

    private IAiChatSessionService aiChatSessionService;
    private AiChatConversationService conversationService;
    private IAiLlmModelService modelService;
    private LlmProvider llmProvider;
    private HistoryWindowPolicyResolver policyResolver;
    private ThreadPoolTaskExecutor executor;
    private AiChatSessionSummaryService summaryService;

    @BeforeEach
    void setUp() {
        aiChatSessionService = mock(IAiChatSessionService.class);
        conversationService = mock(AiChatConversationService.class);
        modelService = mock(IAiLlmModelService.class);
        llmProvider = mock(LlmProvider.class);
        policyResolver = mock(HistoryWindowPolicyResolver.class);
        executor = new ThreadPoolTaskExecutor();
        executor.initialize();
        summaryService = new AiChatSessionSummaryService(
                aiChatSessionService, conversationService, modelService,
                llmProvider, policyResolver, executor);
    }

    /**
     * 前提：会话存在且 assistant 消息数低于触发阈值。
     * 期望：不调用 LLM，不写 context_summary。
     */
    @Test
    void refreshSummaryIfNeeded_skipsWhenAssistantCountLow() throws Exception {
        AiChatSession session = AiChatSession.builder()
                .aiChatSessionId(1L)
                .delStatus(0)
                .build();
        when(aiChatSessionService.selectAiChatSessionById(1L)).thenReturn(session);
        when(conversationService.countAssistantMessages(1L)).thenReturn(1);

        summaryService.refreshSummaryIfNeeded(1L, 100L);

        verify(llmProvider, never()).chat(any(), any());
    }

    /**
     * 前提：assistant 轮次达阈值，模型与历史窗口策略可用。
     * 期望：加载被裁历史调 LLM 生成摘要，并更新 context_summary 与 summaryMessageCount。
     */
    @Test
    void refreshSummaryIfNeeded_updatesSummaryWhenTriggered() throws Exception {
        AiChatSession session = AiChatSession.builder()
                .aiChatSessionId(1L)
                .delStatus(0)
                .summaryMessageCount(0)
                .build();
        LlmModelConfig modelConfig = LlmModelConfig.builder()
                .aiLlmModelId(100L)
                .modelName("gpt-test")
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
        when(llmProvider.chat(any(), any())).thenReturn(LlmChatResponse.builder().content("用户早期确认登录节点").build());

        summaryService.refreshSummaryIfNeeded(1L, 100L);

        verify(conversationService).updateContextSummary(eq(1L), eq("用户早期确认登录节点"), eq(9));
    }
}
