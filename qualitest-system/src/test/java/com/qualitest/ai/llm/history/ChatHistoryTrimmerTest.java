package com.qualitest.ai.llm.history;

import com.qualitest.ai.llm.LlmMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 ChatHistoryTrimmer：按条数 / token 预算裁剪对话历史，并还原被裁前缀。
 * 边界：纯函数，不调用真实 tokenizer。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ChatHistoryTrimmerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatHistoryTrimmerTest {

    private static HistoryWindowPolicy policy(int count, int tokens) {
        return HistoryWindowPolicy.builder().countLimit(count).tokenBudget(tokens).build();
    }

    /**
     * 前提：历史消息列表为空。
     * 期望：trim 返回空列表。
     */
    @Test
    @Order(1)
    @DisplayName("空历史 trim 返回空列表")
    void trim_emptyHistory() {
        assertTrue(ChatHistoryTrimmer.trim(List.of(), policy(10, 1000), 0).isEmpty());
    }

    /**
     * 前提：20 条消息，countLimit=5，token 预算充足。
     * 期望：保留 msg-15～msg-19 共 5 条，顺序不变。
     */
    @Test
    @Order(2)
    @DisplayName("仅 countLimit 时保留末尾 N 条")
    void trim_countLimitOnly() {
        List<LlmMessage> history = IntStream.range(0, 20)
                .mapToObj(i -> LlmMessage.user("msg-" + i))
                .toList();
        List<LlmMessage> trimmed = ChatHistoryTrimmer.trim(history, policy(5, 100_000), 0);
        assertEquals(5, trimmed.size());
        assertEquals("msg-15", trimmed.get(0).getContent());
        assertEquals("msg-19", trimmed.get(4).getContent());
    }

    /**
     * 前提：含两条超长消息与两条短消息，tokenBudget=500。
     * 期望：裁掉部分前缀；最后一条仍为 short-2。
     */
    @Test
    @Order(3)
    @DisplayName("token 预算进一步裁剪前缀")
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

    /**
     * 前提：full 含 old-1、old-2、keep-1、keep-2，trimmed 为后两条。
     * 期望：droppedPrefix 返回 old-1、old-2。
     */
    @Test
    @Order(4)
    @DisplayName("droppedPrefix 返回被裁掉的前缀")
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
