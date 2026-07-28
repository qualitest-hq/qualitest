package com.qualitest.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 ApiSchemaSoftMergeSupport：参数 schema soft merge 与类型变更判定。
 * 边界：纯函数，无 DB；text/string 视为同型。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiSchemaSoftMergeSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiSchemaSoftMergeSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 前提：本地 type=text 含 pattern，上传 type=string 含 description。
     * 期望：不算类型变更；合并后 type=string，保留 pattern 并采用 description。
     */
    @Test
    @Order(1)
    @DisplayName("soft merge：text 与 string 同型，保留本地 pattern")
    void mergeParamNode_textEqualsString_preservesLocalPattern() {
        ObjectNode local = mapper.createObjectNode();
        local.put("name", "bizType");
        local.put("type", "text");
        local.put("pattern", "^[a-z]+$");

        ObjectNode incoming = mapper.createObjectNode();
        incoming.put("name", "bizType");
        incoming.put("type", "string");
        incoming.put("description", "业务分类");

        ObjectNode merged = ApiSchemaSoftMergeSupport.mergeParamNode(local, incoming);
        assertEquals("string", merged.path("type").asText());
        assertEquals("^[a-z]+$", merged.path("pattern").asText());
        assertEquals("业务分类", merged.path("description").asText());
        assertFalse(ApiSchemaSoftMergeSupport.isTypeChanged(local, incoming));
    }

    /**
     * 前提：本地 type=string 含 pattern，上传 type=file。
     * 期望：算类型变更；合并后 type=file，不保留 pattern。
     */
    @Test
    @Order(2)
    @DisplayName("soft merge：string 改 file 算类型变更并丢弃 pattern")
    void mergeParamNode_fileVsString_isTypeChange_dropsPattern() {
        ObjectNode local = mapper.createObjectNode();
        local.put("name", "file");
        local.put("type", "string");
        local.put("pattern", ".+");

        ObjectNode incoming = mapper.createObjectNode();
        incoming.put("name", "file");
        incoming.put("type", "file");

        assertTrue(ApiSchemaSoftMergeSupport.isTypeChanged(local, incoming));
        ObjectNode merged = ApiSchemaSoftMergeSupport.mergeParamNode(local, incoming);
        assertEquals("file", merged.path("type").asText());
        assertTrue(merged.path("pattern").isMissingNode() || merged.path("pattern").isNull());
    }
}
