package com.qualitest.ai.llm.history;

import lombok.Builder;
import lombok.Getter;

/**
 * LLM 多轮历史窗口策略：条数上限 + Token 预算。
 */
@Getter
@Builder
public class HistoryWindowPolicy {

    /** 最多保留的历史消息条数（user + assistant 合计） */
    private final int countLimit;

    /** 历史消息 token 预算上限（不含 system / 本轮 user，由 reservedTokens 另行扣除） */
    private final int tokenBudget;
}
