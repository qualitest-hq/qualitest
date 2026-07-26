package com.qualitest.ai.service;

import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.llm.LlmChatRequest;
import com.qualitest.ai.llm.LlmChatResponse;
import com.qualitest.ai.llm.LlmMessage;
import com.qualitest.ai.llm.LlmModelConfig;
import com.qualitest.ai.llm.LlmProvider;
import com.qualitest.ai.llm.history.HistoryWindowPolicy;
import com.qualitest.ai.llm.history.HistoryWindowPolicyResolver;
import com.qualitest.ai.llm.history.TokenEstimator;
import com.qualitest.ai.scenario.flow.FlowDesignPromptResources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话摘要 Checkpoint：异步压缩被历史窗口裁掉的早期消息，写入 ai_chat_session.context_summary。
 */
@Slf4j
@Service
public class AiChatSessionSummaryService {

    private static final int MIN_ASSISTANT_FOR_TRIGGER = 3;
    private static final int ASSISTANT_INTERVAL = 5;
    private static final int MESSAGE_COUNT_GAP = 10;
    private static final int MAX_SUMMARY_CHARS = 800;

    private final IAiChatSessionService aiChatSessionService;
    private final AiChatConversationService aiChatConversationService;
    private final IAiLlmModelService aiLlmModelService;
    private final LlmProvider llmProvider;
    private final HistoryWindowPolicyResolver historyWindowPolicyResolver;
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    public AiChatSessionSummaryService(
            IAiChatSessionService aiChatSessionService,
            AiChatConversationService aiChatConversationService,
            IAiLlmModelService aiLlmModelService,
            LlmProvider llmProvider,
            HistoryWindowPolicyResolver historyWindowPolicyResolver,
            @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        this.aiChatSessionService = aiChatSessionService;
        this.aiChatConversationService = aiChatConversationService;
        this.aiLlmModelService = aiLlmModelService;
        this.llmProvider = llmProvider;
        this.historyWindowPolicyResolver = historyWindowPolicyResolver;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    }

    /** assistant 落库后异步尝试刷新摘要，不阻塞主流程 */
    public void maybeRefreshSummaryAsync(Long sessionId, Long modelId) {
        if (sessionId == null || modelId == null) {
            return;
        }
        threadPoolTaskExecutor.execute(() -> {
            try {
                refreshSummaryIfNeeded(sessionId, modelId);
            } catch (Exception e) {
                log.warn("会话摘要刷新失败 sessionId={}: {}", sessionId, e.getMessage());
            }
        });
    }

    void refreshSummaryIfNeeded(Long sessionId, Long modelId) throws IOException {
        AiChatSession session = aiChatSessionService.selectAiChatSessionById(sessionId);
        if (session == null || (session.getDelStatus() != null && session.getDelStatus() != 0)) {
            return;
        }

        int assistantCount = aiChatConversationService.countAssistantMessages(sessionId);
        int totalMessages = aiChatConversationService.countSessionMessages(sessionId);
        if (!shouldTrigger(assistantCount, totalMessages, session.getSummaryMessageCount())) {
            return;
        }

        LlmModelConfig modelConfig = aiLlmModelService.resolve(modelId);
        HistoryWindowPolicy policy = historyWindowPolicyResolver.resolve(modelConfig);
        int reservedTokens = estimateReservedTokens(session);
        List<LlmMessage> dropped = aiChatConversationService.loadDroppedHistoryForSummary(
                sessionId, modelConfig, policy, reservedTokens);
        if (dropped.isEmpty()) {
            return;
        }

        String droppedText = aiChatConversationService.formatMessagesForSummary(dropped);
        if (droppedText.isBlank()) {
            return;
        }

        String summary = callSummaryLlm(modelConfig, session.getContextSummary(), droppedText);
        if (summary == null || summary.isBlank()) {
            return;
        }
        if (summary.length() > MAX_SUMMARY_CHARS) {
            summary = summary.substring(0, MAX_SUMMARY_CHARS);
        }

        int retained = aiChatConversationService.loadMessagesForLlm(
                sessionId, modelConfig, policy, reservedTokens).size();
        int coveredCount = Math.max(0, totalMessages - retained);
        aiChatConversationService.updateContextSummary(sessionId, summary.trim(), coveredCount);
    }

    private static boolean shouldTrigger(int assistantCount, int totalMessages, Integer summaryMessageCount) {
        if (assistantCount < MIN_ASSISTANT_FOR_TRIGGER) {
            return false;
        }
        if (assistantCount % ASSISTANT_INTERVAL == 0) {
            return true;
        }
        if (summaryMessageCount == null) {
            return assistantCount >= MIN_ASSISTANT_FOR_TRIGGER;
        }
        return totalMessages - summaryMessageCount > MESSAGE_COUNT_GAP;
    }

    private static int estimateReservedTokens(AiChatSession session) {
        int reserved = 4096;
        if (session.getContextSummary() != null && !session.getContextSummary().isBlank()) {
            reserved += TokenEstimator.estimateText(session.getContextSummary()) + 20;
        }
        return reserved;
    }

    private String callSummaryLlm(LlmModelConfig modelConfig, String existingSummary, String droppedText)
            throws IOException {
        String systemPrompt = FlowDesignPromptResources.loadText("ai/session-summary-prompt.txt");
        StringBuilder userContent = new StringBuilder();
        if (existingSummary != null && !existingSummary.isBlank()) {
            userContent.append("【已有摘要】\n").append(existingSummary.trim()).append("\n\n");
        }
        userContent.append("【待压缩的早期对话】\n").append(droppedText);

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(systemPrompt));
        messages.add(LlmMessage.user(userContent.toString()));

        LlmChatRequest request = LlmChatRequest.builder()
                .messages(messages)
                .stream(false)
                .build();
        LlmChatResponse response = llmProvider.chat(modelConfig, request);
        if (response == null || response.getContent() == null) {
            return null;
        }
        return response.getContent().trim();
    }
}
