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
 * 会话摘要 Checkpoint 服务单元测试。
 *
 * 验证 assistant 消息数不足时跳过摘要生成；
 * 达到触发条件时拉取被裁掉的历史、调用 LLM 生成摘要并写回会话。
 * 依赖 Mock，不调用真实 LLM 与数据库。
 *
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=AiChatSessionSummaryServiceTest
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

    /** assistant 轮次过少时不应调用 LLM 生成摘要 */
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

    /** 满足触发条件时应加载被裁历史、生成摘要并更新 context_summary 字段 */
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
