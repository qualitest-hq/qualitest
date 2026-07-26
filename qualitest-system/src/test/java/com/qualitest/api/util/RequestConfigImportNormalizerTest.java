package com.qualitest.api.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RequestConfigImportNormalizer} 单元测试。
 * 覆盖：标准结构通过、非法键/缺版本失败、text→string、file 位置矫正。
 */
class RequestConfigImportNormalizerTest {

    /** 合法标准结构可规范化成功，并含 configVersion、queryParams。 */
    @Test
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

    /** 顶层含 params 键时抛业务异常。 */
    @Test
    void normalize_rejectsParamsKey() {
        String raw = """
                {"configVersion":1,"method":"GET","params":[]}
                """;
        ServiceException ex = assertThrows(ServiceException.class,
                () -> RequestConfigImportNormalizer.normalize(raw));
        assertTrue(ex.getMessage().contains("params"));
    }

    /** 缺少 configVersion 时抛业务异常。 */
    @Test
    void normalize_rejectsMissingConfigVersion() {
        String raw = """
                {"method":"GET","queryParams":[]}
                """;
        assertThrows(ServiceException.class, () -> RequestConfigImportNormalizer.normalize(raw));
    }

    /** formData：file 保留，text / TEXT 均改为 string。 */
    @Test
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
     * query、path、headers、urlencoded 上的 file 一律改为 string
     *（file 只允许出现在 formData）。
     */
    @Test
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

    /** query 参数 type=text 改为 string。 */
    @Test
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
