package com.qualitest.ai.tools.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 AssetVariablesListingSupport：素材 JSON 转 AI 安全视图（无明文）。
 * 边界：纯函数，无 DB / Service。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AssetVariablesListingSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssetVariablesListingSupportTest {

    /**
     * 前提：一条 login 素材，assets 以 key 包装 phone/password 明文。
     * 期望：items 含 key/remark/fields/placeholderHint；序列化结果无 secret、138；truncated=false。
     */
    @Test
    @Order(1)
    @DisplayName("包装结构只暴露字段名不含值")
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

    /**
     * 前提：asset_variables JSON 为 null。
     * 期望：items 为空，truncated=false。
     */
    @Test
    @Order(2)
    @DisplayName("空 JSON 时 items 为空")
    void buildListResult_blank_returnsEmptyItems() {
        JSONObject result = AssetVariablesListingSupport.buildListResult(null);

        assertEquals(0, result.getJSONArray("items").size());
        assertFalse(result.getBooleanValue("truncated"));
    }

    /**
     * 前提：条目数 = MAX_ITEMS + 3。
     * 期望：items 长度等于 MAX_ITEMS，truncated=true。
     */
    @Test
    @Order(3)
    @DisplayName("超限时截断并标 truncated")
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
