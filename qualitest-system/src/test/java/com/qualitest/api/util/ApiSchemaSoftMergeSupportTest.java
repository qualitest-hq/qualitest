package com.qualitest.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApiSchemaSoftMergeSupport} 单元测试。
 * 覆盖：text/string 类型比较、类型变更时是否保留用户约束。
 */
class ApiSchemaSoftMergeSupportTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 本地 type=text、上传 type=string：不算类型变更，合并后保留本地 pattern，
     * 结构字段采用上传包（如 description）。
     */
    @Test
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
     * 本地 type=string、上传 type=file：算类型变更，不把本地 pattern 写回结果。
     */
    @Test
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
