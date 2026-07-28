package com.qualitest.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 AiAgentRunnerToolResult：工具返回 JSON 是否含 error 字段的判定。
 * 边界：纯静态方法，无 IO。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AiAgentRunnerToolResultTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiAgentRunnerToolResultTest {

    /**
     * 前提：工具返回 JSON 含 error 字段、正常 items 结果、或 null。
     * 期望：含 error 返回 true，items 与 null 返回 false。
     */
    @Test
    @Order(1)
    @DisplayName("含 error 字段判定为工具错误")
    void isToolErrorResult_detectsErrorField() {
        assertTrue(AiAgentRunner.isToolErrorResult("{\"error\":\"未知工具\"}"));
        assertFalse(AiAgentRunner.isToolErrorResult("{\"items\":[]}"));
        assertFalse(AiAgentRunner.isToolErrorResult(null));
    }
}
