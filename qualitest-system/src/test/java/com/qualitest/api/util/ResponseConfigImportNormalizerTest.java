package com.qualitest.api.util;

import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 ResponseConfigImportNormalizer：导入响应配置规范化与非法结构拒绝。
 * 边界：纯函数，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ResponseConfigImportNormalizerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResponseConfigImportNormalizerTest {

    /**
     * 前提：合法 v2 responseConfig 含 responses 数组。
     * 期望：normalize 成功；输出含 configVersion、responses 与 resp id。
     */
    @Test
    @Order(1)
    @DisplayName("规范化：接受合法 v2 responses 数组")
    void normalize_acceptsV2ResponsesArray() {
        String raw = """
                {
                  "configVersion": 1,
                  "responses": [{
                    "id": "resp-abc123",
                    "name": "成功",
                    "httpStatus": 200,
                    "contentType": "json",
                    "schema": {"type": "object"}
                  }]
                }
                """;
        String out = ResponseConfigImportNormalizer.normalize(raw);
        assertTrue(out.contains("\"configVersion\":1"));
        assertTrue(out.contains("\"responses\""));
        assertTrue(out.contains("resp-abc123"));
    }

    /**
     * 前提：responseConfig 含非法 content 字段。
     * 期望：抛 ServiceException，消息含 content。
     */
    @Test
    @Order(2)
    @DisplayName("规范化：拒绝非法 content 字段")
    void normalize_rejectsContentField() {
        String raw = """
                {
                  "configVersion": 1,
                  "description": "ok",
                  "content": {"application/json": {"schema": {"type": "object"}}}
                }
                """;
        ServiceException ex = assertThrows(ServiceException.class,
                () -> ResponseConfigImportNormalizer.normalize(raw));
        assertTrue(ex.getMessage().contains("content"));
    }

    /**
     * 前提：入参非合法 JSON 字符串。
     * 期望：抛 ServiceException。
     */
    @Test
    @Order(3)
    @DisplayName("规范化：非法 JSON 时拒绝")
    void normalize_rejectsInvalidJson() {
        assertThrows(ServiceException.class, () -> ResponseConfigImportNormalizer.normalize("not-json"));
    }

    /**
     * 前提：responseConfig 缺少 responses 数组。
     * 期望：抛 ServiceException。
     */
    @Test
    @Order(4)
    @DisplayName("规范化：缺少 responses 时拒绝")
    void normalize_rejectsMissingResponsesArray() {
        String raw = """
                {"configVersion": 1}
                """;
        assertThrows(ServiceException.class, () -> ResponseConfigImportNormalizer.normalize(raw));
    }
}
