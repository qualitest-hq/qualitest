package com.qualitest.ai.llm.history;

import com.qualitest.ai.llm.LlmMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 按条数上限与 token 预算裁剪多轮历史（保留尾部最近消息）。
 */
public final class ChatHistoryTrimmer {

    private ChatHistoryTrimmer() {
    }

    /**
     * @param history        升序完整历史
     * @param policy         条数 / token 策略
     * @param reservedTokens 为 system、会话摘要、本轮 user 等预留的 token
     */
    public static List<LlmMessage> trim(List<LlmMessage> history, HistoryWindowPolicy policy, int reservedTokens) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        if (policy == null) {
            return new ArrayList<>(history);
        }

        int countLimit = policy.getCountLimit() > 0 ? policy.getCountLimit() : history.size();
        int startByCount = Math.max(0, history.size() - countLimit);
        List<LlmMessage> byCount = history.subList(startByCount, history.size());

        int effectiveBudget = Math.max(0, policy.getTokenBudget() - Math.max(0, reservedTokens));
        if (effectiveBudget <= 0) {
            return byCount.isEmpty() ? List.of() : List.of(byCount.get(byCount.size() - 1));
        }

        List<LlmMessage> result = new ArrayList<>();
        int used = 0;
        for (int i = byCount.size() - 1; i >= 0; i--) {
            LlmMessage message = byCount.get(i);
            int cost = TokenEstimator.estimateMessage(message);
            if (!result.isEmpty() && used + cost > effectiveBudget) {
                break;
            }
            used += cost;
            result.add(0, message);
            if (result.size() == 1 && cost > effectiveBudget) {
                break;
            }
        }
        return result;
    }

    /** 返回被裁剪掉的头部消息（升序） */
    public static List<LlmMessage> droppedPrefix(List<LlmMessage> fullHistory, List<LlmMessage> trimmed) {
        if (fullHistory == null || fullHistory.isEmpty()) {
            return List.of();
        }
        if (trimmed == null || trimmed.isEmpty()) {
            return new ArrayList<>(fullHistory);
        }
        if (trimmed.size() >= fullHistory.size()) {
            return List.of();
        }
        int dropped = fullHistory.size() - trimmed.size();
        return Collections.unmodifiableList(new ArrayList<>(fullHistory.subList(0, dropped)));
    }
}
