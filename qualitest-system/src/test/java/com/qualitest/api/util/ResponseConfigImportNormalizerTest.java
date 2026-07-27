package com.qualitest.api.util;

import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ResponseConfigImportNormalizer} 单元测试
 * <p>
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ResponseConfigImportNormalizerTest
 */
class ResponseConfigImportNormalizerTest {

    /**
     * 前提：合法 v2 responseConfig 含 responses 数组。
     * 期望：normalize 成功；输出含 configVersion、responses 与 resp id。
     */
    @Test
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
        assertThrows(ServiceException.class, () -> ResponseConfigImportNormalizer.normalize("not-json"));
    }

    /**
     * 前提：responseConfig 缺少 responses 数组。
     * 期望：抛 ServiceException。
     */
    @Test
        String raw = """
                {"configVersion": 1}
                """;
        assertThrows(ServiceException.class, () -> ResponseConfigImportNormalizer.normalize(raw));
    }
}
