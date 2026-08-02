package com.qualitest.flow.http;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 HttpNodeRequestValueOverridesSupport：overrides 形状纠正与落盘。
 * 边界：顶层误放 body 字段；已有 bodyExample 时的合并；合法形状不变。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpNodeRequestValueOverridesSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpNodeRequestValueOverridesSupportTest {

    /**
     * 前提：overrides 顶层直接写 cartIds/addressId，无 bodyExample。
     * 期望：提升为 bodyExample，顶层不再残留业务字段。
     */
    @Test
    @Order(1)
    @DisplayName("顶层 body 字段提升为 bodyExample")
    void normalize_liftsStrayBodyFieldsIntoBodyExample() {
        JSONObject raw = new JSONObject();
        raw.put("cartIds", List.of(5001, 5002));
        raw.put("addressId", 4001);

        JSONObject normalized = HttpNodeRequestValueOverridesSupport.normalizeOverridesShape(raw);

        assertFalse(normalized.containsKey("cartIds"));
        assertFalse(normalized.containsKey("addressId"));
        JSONObject body = normalized.getJSONObject("bodyExample");
        assertEquals(4001, body.getIntValue("addressId"));
        assertEquals(2, body.getJSONArray("cartIds").size());
        assertTrue(body.getJSONArray("cartIds").contains(5001));
        assertTrue(body.getJSONArray("cartIds").contains(5002));
    }

    /**
     * 前提：已有 bodyExample 对象，另有顶层误放 cartIds。
     * 期望：cartIds 合并进 bodyExample，原 addressId 保留。
     */
    @Test
    @Order(2)
    @DisplayName("已有 bodyExample 时合并误放字段")
    void normalize_mergesStrayIntoExistingBodyExample() {
        JSONObject body = new JSONObject();
        body.put("addressId", 4001);
        JSONObject raw = new JSONObject();
        raw.put("bodyExample", body);
        raw.put("cartIds", List.of(5001, 5002));

        HttpNodeRequestValueOverridesSupport.normalizeOverridesShapeInPlace(raw);

        assertFalse(raw.containsKey("cartIds"));
        JSONObject merged = raw.getJSONObject("bodyExample");
        assertEquals(4001, merged.getIntValue("addressId"));
        assertEquals(2, merged.getJSONArray("cartIds").size());
        assertTrue(merged.getJSONArray("cartIds").contains(5001));
    }

    /**
     * 前提：仅 paramDefaults + bodyExample 的合法形状。
     * 期望：normalize 后内容不变。
     */
    @Test
    @Order(3)
    @DisplayName("合法形状保持不变")
    void normalize_keepsValidShapeUnchanged() {
        JSONObject raw = new JSONObject();
        raw.put("paramDefaults", Map.of("q", "1"));
        raw.put("bodyExample", Map.of("addressId", 4001, "cartIds", List.of(5001)));

        JSONObject normalized = HttpNodeRequestValueOverridesSupport.normalizeOverridesShape(raw);

        assertEquals("1", normalized.getJSONObject("paramDefaults").getString("q"));
        assertEquals(4001, normalized.getJSONObject("bodyExample").getIntValue("addressId"));
        assertFalse(normalized.containsKey("cartIds"));
    }

    /**
     * 前提：toPersistMap 收到顶层误放 body 字段。
     * 期望：落盘 Map 只有 bodyExample。
     */
    @Test
    @Order(4)
    @DisplayName("toPersistMap 落盘前纠正形状")
    void toPersistMap_normalizesBeforePersist() {
        JSONObject raw = new JSONObject();
        raw.put("addressId", 4001);
        raw.put("cartIds", List.of(5001, 5002));

        Map<String, Object> persisted = HttpNodeRequestValueOverridesSupport.toPersistMap(raw);

        assertNull(persisted.get("cartIds"));
        assertTrue(persisted.containsKey("bodyExample"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) persisted.get("bodyExample");
        assertEquals(4001, body.get("addressId"));
    }
}
