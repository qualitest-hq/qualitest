package com.qualitest.api.util;

import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ResponseConfigImportNormalizer} 单元测试 */
class ResponseConfigImportNormalizerTest {

    @Test
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

    @Test
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

    @Test
    void normalize_rejectsInvalidJson() {
        assertThrows(ServiceException.class, () -> ResponseConfigImportNormalizer.normalize("not-json"));
    }

    @Test
    void normalize_rejectsMissingResponsesArray() {
        String raw = """
                {"configVersion": 1}
                """;
        assertThrows(ServiceException.class, () -> ResponseConfigImportNormalizer.normalize(raw));
    }
}
