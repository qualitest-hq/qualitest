package com.qualitest.ai.tools.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AssetVariablesListingSupport：解析素材库 JSON 为工具返回（不含明文值）。
 */
class AssetVariablesListingSupportTest {

    @Test
    @DisplayName("包装结构：返回 key/fields/placeholderHint，不含字段值")
    void buildListResult_wrappedAssets_exposesFieldsOnly() {
        String json = """
                [{"id":1,"key":"login","remark":"账号","updateTime":"2026-01-01 00:00:00",
                  "assets":{"login":{"phone":"138","password":"secret"}}}]
                """;
        JSONObject result = AssetVariablesListingSupport.buildListResult(json);
        JSONArray items = result.getJSONArray("items");
        assertEquals(1, items.size());
        JSONObject item = items.getJSONObject(0);
        assertEquals("login", item.getString("key"));
        assertEquals("账号", item.getString("remark"));
        assertEquals("{{asset.login.<field>}}", item.getString("placeholderHint"));
        JSONArray fields = item.getJSONArray("fields");
        assertTrue(fields.contains("phone"));
        assertTrue(fields.contains("password"));
        assertFalse(result.toJSONString().contains("secret"));
        assertFalse(result.toJSONString().contains("138"));
        assertFalse(result.getBooleanValue("truncated"));
    }

    @Test
    @DisplayName("空 JSON：items 为空")
    void buildListResult_blank_returnsEmptyItems() {
        JSONObject result = AssetVariablesListingSupport.buildListResult(null);
        assertEquals(0, result.getJSONArray("items").size());
        assertFalse(result.getBooleanValue("truncated"));
    }

    @Test
    @DisplayName("超限：truncated 为 true 且 items 不超过上限")
    void buildListResult_overLimit_setsTruncated() {
        StringBuilder sb = new StringBuilder("[");
        int n = AssetVariablesListingSupport.MAX_ITEMS + 3;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(i)
                    .append(",\"key\":\"k").append(i)
                    .append("\",\"assets\":{\"k").append(i).append("\":{\"f\":1}}}");
        }
        sb.append(']');
        JSONObject result = AssetVariablesListingSupport.buildListResult(sb.toString());
        assertEquals(AssetVariablesListingSupport.MAX_ITEMS, result.getJSONArray("items").size());
        assertTrue(result.getBooleanValue("truncated"));
    }
}
