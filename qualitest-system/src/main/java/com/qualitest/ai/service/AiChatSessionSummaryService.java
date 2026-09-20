package com.qualitest.ai.service;

import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.llm.LlmMessage;
import com.qualitest.ai.llm.LlmModelConfig;
import com.qualitest.ai.llm.history.HistoryWindowPolicy;
import com.qualitest.ai.llm.history.HistoryWindowPolicyResolver;
import com.qualitest.ai.llm.history.TokenEstimator;
import com.qualitest.ai.llm.lc4j.Lc4jClientFactory;
import com.qualitest.ai.scenario.flow.FlowDesignPromptResources;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * 会话摘要服务。
 * <p>
 * 在助手消息落库后异步判断是否需要刷新摘要：把历史窗口裁掉的早期对话压缩成短文本，
 * 写入会话表的 context_summary，供后续轮次作为系统侧背景，减少重复塞入超长历史。
 */
@Slf4j
@Service
public class AiChatSessionSummaryService {

    /** 至少产生这么多条助手消息后才考虑触发摘要。 */
    private static final int MIN_ASSISTANT_FOR_TRIGGER = 3;
    /** 助手消息条数每增加该间隔触发一次。 */
    private static final int ASSISTANT_INTERVAL = 5;
    /** 相对上次摘要覆盖的消息数，缺口超过该值也触发。 */
    private static final int MESSAGE_COUNT_GAP = 10;
    /** 摘要正文最大字符数，超出截断。 */
    private static final int MAX_SUMMARY_CHARS = 800;

    private final IAiChatSessionService aiChatSessionService;
    private final AiChatConversationService aiChatConversationService;
    private final IAiLlmModelService aiLlmModelService;
    private final Lc4jClientFactory lc4jClientFactory;
    private final HistoryWindowPolicyResolver historyWindowPolicyResolver;
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    public AiChatSessionSummaryService(
            IAiChatSessionService aiChatSessionService,
            AiChatConversationService aiChatConversationService,
            IAiLlmModelService aiLlmModelService,
            Lc4jClientFactory lc4jClientFactory,
            HistoryWindowPolicyResolver historyWindowPolicyResolver,
            @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        this.aiChatSessionService = aiChatSessionService;
        this.aiChatConversationService = aiChatConversationService;
        this.aiLlmModelService = aiLlmModelService;
        this.lc4jClientFactory = lc4jClientFactory;
        this.historyWindowPolicyResolver = historyWindowPolicyResolver;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    }

    /**
     * 助手落库后异步尝试刷新摘要，不阻塞主流程。
     * sessionId 或 modelId 为空时直接返回。
     */
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

    /**
     * 同步判断并刷新摘要。
     * 会话不存在或已删除则跳过；未达触发条件、无被裁历史、或模型返回空则跳过。
     */
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

    /**
     * 是否应触发摘要刷新。
     * 条件：助手条数达标，且（条数整除间隔，或尚未有摘要覆盖数，或相对上次覆盖缺口过大）。
     */
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

    /**
     * 估算为系统提示与既有摘要预留的 token，避免装载「被裁历史」时算错窗口。
     */
    private static int estimateReservedTokens(AiChatSession session) {
        int reserved = 4096;
        if (session.getContextSummary() != null && !session.getContextSummary().isBlank()) {
            reserved += TokenEstimator.estimateText(session.getContextSummary()) + 20;
        }
        return reserved;
    }

    /**
     * 调用同步对话模型生成新摘要。
     * 关闭思考链以降低延迟与费用；入参含已有摘要与待压缩的早期对话文本。
     *
     * @return 模型返回的摘要正文；无有效回复时为 null
     */
    private String callSummaryLlm(LlmModelConfig modelConfig, String existingSummary, String droppedText)
            throws IOException {
        String systemPrompt = FlowDesignPromptResources.loadText("ai/session-summary-prompt.txt");
        StringBuilder userContent = new StringBuilder();
        if (existingSummary != null && !existingSummary.isBlank()) {
            userContent.append("【已有摘要】\n").append(existingSummary.trim()).append("\n\n");
        }
        userContent.append("【待压缩的早期对话】\n").append(droppedText);

        boolean reasoningEnabled = false;
        ChatModel chatModel = lc4jClientFactory.chatModel(modelConfig, reasoningEnabled);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userContent.toString())))
                .build();
        ChatResponse response = chatModel.chat(request);
        if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
            return null;
        }
        return response.aiMessage().text().trim();
    }
}
