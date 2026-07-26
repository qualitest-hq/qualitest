package com.qualitest.ai.llm.history;

import com.qualitest.ai.llm.LlmMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token 启发式估算单元测试。
 *
 * 验证空文本、中英文混合、单条 LlmMessage 的 token 估算行为。
 * 估算用于历史裁剪时的 token 预算控制，不调用真实 tokenizer。
 *
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=TokenEstimatorTest
 */
class TokenEstimatorTest {

    /** null 或空字符串应返回 0 */
    @Test
    void estimateText_emptyReturnsZero() {
        assertEquals(0, TokenEstimator.estimateText(null));
        assertEquals(0, TokenEstimator.estimateText(""));
    }

    /** 中文同等字符数应比英文估算值更高 */
    @Test
    void estimateText_mixedContent() {
        int english = TokenEstimator.estimateText("hello world test");
        int chinese = TokenEstimator.estimateText("添加登录校验节点");
        assertTrue(chinese > english);
        assertTrue(chinese > 0);
    }

    /** 单条消息的估算应包含 role 等固定开销，大于纯文本估算 */
    @Test
    void estimateMessage_includesOverhead() {
        int textOnly = TokenEstimator.estimateText("hello");
        int message = TokenEstimator.estimateMessage(LlmMessage.user("hello"));
        assertTrue(message > textOnly);
    }
}
