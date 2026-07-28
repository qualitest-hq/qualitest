package com.qualitest.ai.llm.history;

import com.qualitest.ai.llm.LlmMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 TokenEstimator：历史裁剪用的启发式 token 估算。
 * 边界：纯函数，不调用真实 tokenizer。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TokenEstimatorTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TokenEstimatorTest {

    /**
     * 前提：estimateText 入参为 null 或空串。
     * 期望：均返回 0。
     */
    @Test
    @Order(1)
    @DisplayName("文本估算：空入参返回 0")
    void estimateText_emptyReturnsZero() {
        assertEquals(0, TokenEstimator.estimateText(null));
        assertEquals(0, TokenEstimator.estimateText(""));
    }

    /**
     * 前提：同等长度的英文与中文纯文本。
     * 期望：中文估算 token 数高于英文且大于 0。
     */
    @Test
    @Order(2)
    @DisplayName("文本估算：中文高于英文")
    void estimateText_mixedContent() {
        int english = TokenEstimator.estimateText("hello world test");
        int chinese = TokenEstimator.estimateText("添加登录校验节点");
        assertTrue(chinese > english);
        assertTrue(chinese > 0);
    }

    /**
     * 前提：同内容 user 消息与纯文本 estimateText。
     * 期望：estimateMessage 大于纯文本估算（含 role 等开销）。
     */
    @Test
    @Order(3)
    @DisplayName("消息估算：含 role 等开销高于纯文本")
    void estimateMessage_includesOverhead() {
        int textOnly = TokenEstimator.estimateText("hello");
        int message = TokenEstimator.estimateMessage(LlmMessage.user("hello"));
        assertTrue(message > textOnly);
    }
}
