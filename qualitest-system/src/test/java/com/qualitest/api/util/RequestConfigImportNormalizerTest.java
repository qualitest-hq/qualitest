package com.qualitest.api.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 RequestConfigImportNormalizer：导入请求配置规范化与非法结构拒绝。
 * 边界：纯函数，无 DB；含 text→string、file 仅允许 formData。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=RequestConfigImportNormalizerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RequestConfigImportNormalizerTest {

    /**
     * 前提：合法 v2 requestConfig JSON（含 configVersion、queryParams）。
     * 期望：normalize 成功；输出含 configVersion 与 queryParams，无 params 键。
     */
    @Test
    @Order(1)
    @DisplayName("规范化：接受合法 v2 结构")
    void normalize_acceptsV2Shape() throws Exception {
        String raw = """
                {
                  "configVersion": 1,
                  "method": "POST",
                  "queryParams": [{"name": "page", "in": "query"}],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {"mode": "none", "json": {"schema": null, "example": null}}
                }
                """;
        String out = RequestConfigImportNormalizer.normalize(raw);
        assertTrue(out.contains("\"configVersion\":1"));
        assertTrue(out.contains("\"queryParams\""));
        assertTrue(!out.contains("\"params\""));
    }

    /**
     * 前提：requestConfig 顶层含非法 params 键。
     * 期望：抛 ServiceException，消息含 params。
     */
    @Test
    @Order(2)
    @DisplayName("规范化：拒绝顶层 params 键")
    void normalize_rejectsParamsKey() {
        String raw = """
                {"configVersion":1,"method":"GET","params":[]}
                """;
        ServiceException ex = assertThrows(ServiceException.class,
                () -> RequestConfigImportNormalizer.normalize(raw));
        assertTrue(ex.getMessage().contains("params"));
    }

    /**
     * 前提：requestConfig 缺少 configVersion。
     * 期望：抛 ServiceException。
     */
    @Test
    @Order(3)
    @DisplayName("规范化：缺少 configVersion 时拒绝")
    void normalize_rejectsMissingConfigVersion() {
        String raw = """
                {"method":"GET","queryParams":[]}
                """;
        assertThrows(ServiceException.class, () -> RequestConfigImportNormalizer.normalize(raw));
    }

    /**
     * 前提：formData 含 file、text、TEXT 类型项。
     * 期望：file 保留；text/TEXT 均规范为 string。
     */
    @Test
    @Order(4)
    @DisplayName("规范化：formData 中 text 变 string，file 保留")
    void normalize_formData_textToString_keepsFile() throws Exception {
        String raw = """
                {
                  "configVersion": 1,
                  "method": "POST",
                  "queryParams": [],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {
                    "mode": "form-data",
                    "formData": [
                      {"name": "file", "type": "file", "required": true},
                      {"name": "bizType", "type": "text", "required": false},
                      {"name": "note", "type": "TEXT"}
                    ]
                  }
                }
                """;
        JsonNode root = ApiConfigJsonSupport.readTree(RequestConfigImportNormalizer.normalize(raw));
        JsonNode formData = root.path("body").path("formData");
        assertEquals("file", formData.get(0).path("type").asText());
        assertEquals("string", formData.get(1).path("type").asText());
        assertEquals("string", formData.get(2).path("type").asText());
    }

    /**
     * 前提：query/path/headers/urlencoded 参数 type=file。
     * 期望：normalize 后均改为 string（file 仅允许 formData）。
     */
    @Test
    @Order(5)
    @DisplayName("规范化：非 formData 的 file 强制改为 string")
    void normalize_fileOutsideFormData_coercesToString() throws Exception {
        String raw = """
                {
                  "configVersion": 1,
                  "method": "GET",
                  "queryParams": [{"name": "qFile", "type": "file"}],
                  "pathParams": [{"name": "pFile", "type": "File"}],
                  "declaredHeaders": [{"name": "X-File", "type": "file"}],
                  "body": {
                    "mode": "x-www-form-urlencoded",
                    "urlencoded": [{"name": "uFile", "type": "file"}]
                  }
                }
                """;
        JsonNode root = ApiConfigJsonSupport.readTree(RequestConfigImportNormalizer.normalize(raw));
        assertEquals("string", root.path("queryParams").get(0).path("type").asText());
        assertEquals("string", root.path("pathParams").get(0).path("type").asText());
        assertEquals("string", root.path("declaredHeaders").get(0).path("type").asText());
        assertEquals("string", root.path("body").path("urlencoded").get(0).path("type").asText());
    }

    /**
     * 前提：queryParams 单项 type=text。
     * 期望：normalize 后 type 变为 string。
     */
    @Test
    @Order(6)
    @DisplayName("规范化：query 参数 text 变为 string")
    void normalize_queryParam_textToString() throws Exception {
        String raw = """
                {
                  "configVersion": 1,
                  "method": "GET",
                  "queryParams": [{"name": "kw", "type": "text"}],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {"mode": "none"}
                }
                """;
        JsonNode root = ApiConfigJsonSupport.readTree(RequestConfigImportNormalizer.normalize(raw));
        assertEquals("string", root.path("queryParams").get(0).path("type").asText());
    }
}
