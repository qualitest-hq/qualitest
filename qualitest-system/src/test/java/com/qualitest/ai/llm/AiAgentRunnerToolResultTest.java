package com.qualitest.ai.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiAgentRunner#isToolErrorResult} 单元测试：验证工具执行结果的失败判定逻辑。
 * <p>
 * Agent 循环中，工具返回 JSON 若含 {@code "error"} 字段则视为工具调用失败，
 *  Runner 会将该结果标记为 error 并决定是否重试或终止。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=AiAgentRunnerToolResultTest
 */
class AiAgentRunnerToolResultTest {

    /**
     * JSON 含 error 字段 → true；正常 items 结果 → false；null → false。
     */
    @Test
    void isToolErrorResult_detectsErrorField() {
        assertTrue(AiAgentRunner.isToolErrorResult("{\"error\":\"未知工具\"}"));
        assertFalse(AiAgentRunner.isToolErrorResult("{\"items\":[]}"));
        assertFalse(AiAgentRunner.isToolErrorResult(null));
    }
}
