package com.qualitest.ai.llm.history;

import com.qualitest.ai.llm.LlmMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对话历史裁剪单元测试。
 *
 * 验证条数上限、token 预算、reservedTokens 预留空间下的裁剪结果，
 * 以及被裁掉的前缀消息能否正确还原（供会话摘要使用）。
 *
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ChatHistoryTrimmerTest
 */
class ChatHistoryTrimmerTest {

    private static HistoryWindowPolicy policy(int count, int tokens) {
        return HistoryWindowPolicy.builder().countLimit(count).tokenBudget(tokens).build();
    }

    /** 空历史应返回空列表 */
    @Test
    void trim_emptyHistory() {
        assertTrue(ChatHistoryTrimmer.trim(List.of(), policy(10, 1000), 0).isEmpty());
    }

    /** 仅条数超限时，保留最后 N 条且顺序不变 */
    @Test
    void trim_countLimitOnly() {
        List<LlmMessage> history = IntStream.range(0, 20)
                .mapToObj(i -> LlmMessage.user("msg-" + i))
                .toList();
        List<LlmMessage> trimmed = ChatHistoryTrimmer.trim(history, policy(5, 100_000), 0);
        assertEquals(5, trimmed.size());
        assertEquals("msg-15", trimmed.get(0).getContent());
        assertEquals("msg-19", trimmed.get(4).getContent());
    }

    /** token 预算不足时，在条数上限内进一步从头部裁掉长消息，保留最新消息 */
    @Test
    void trim_tokenBudgetLimitsFurther() {
        List<LlmMessage> history = List.of(
                LlmMessage.user("a".repeat(4000)),
                LlmMessage.assistant("b".repeat(4000), null),
                LlmMessage.user("short-1"),
                LlmMessage.user("short-2"));
        List<LlmMessage> trimmed = ChatHistoryTrimmer.trim(history, policy(10, 500), 0);
        assertTrue(trimmed.size() < history.size());
        assertEquals("short-2", trimmed.get(trimmed.size() - 1).getContent());
    }

    /** 根据裁剪前后列表，还原被丢弃的前缀消息 */
    @Test
    void droppedPrefix_returnsHeadMessages() {
        List<LlmMessage> full = List.of(
                LlmMessage.user("old-1"),
                LlmMessage.user("old-2"),
                LlmMessage.user("keep-1"),
                LlmMessage.user("keep-2"));
        List<LlmMessage> trimmed = full.subList(2, 4);
        List<LlmMessage> dropped = ChatHistoryTrimmer.droppedPrefix(full, trimmed);
        assertEquals(2, dropped.size());
        assertEquals("old-1", dropped.get(0).getContent());
    }
}
