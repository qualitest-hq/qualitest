package com.qualitest.project.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：ManagedAuthHeaderApplier——设计期只剥托管头；extracts 路径比对（认票用）。
 * 边界：剥 profileManaged；显式头不动；extract 写出模板路径可检出。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ManagedAuthHeaderApplierTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ManagedAuthHeaderApplierTest {

    /**
     * 前提：节点上有 profileManaged 行与手工行。
     * 期望：只剥托管行，手工行保留。
     */
    @Test
    @Order(1)
    @DisplayName("设计期剥掉 profileManaged，保留手工头")
    void strip_removesManagedKeepsExplicit() {
        Map<String, Object> managed = new LinkedHashMap<>();
        managed.put("_enabled", true);
        managed.put("name", "Authorization");
        managed.put("value", "Bearer {{asset.clientAuth.token}}");
        managed.put(AuthHeaderResolver.PROFILE_MANAGED, true);
        Map<String, Object> explicit = new LinkedHashMap<>();
        explicit.put("_enabled", true);
        explicit.put("name", "X-Trace");
        explicit.put("value", "1");

        Map<String, Object> data = new HashMap<>();
        data.put("headers", new ArrayList<>(List.of(managed, explicit)));

        assertTrue(ManagedAuthHeaderApplier.stripManagedFromNodeData(data));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> headers = (List<Map<String, Object>>) data.get("headers");
        assertTrue(headers.size() == 1);
        assertTrue("X-Trace".equals(headers.get(0).get("name")));
    }

    /**
     * 前提：extracts 写出 asset.clientAuth.token。
     * 期望：extractsWriteAnyOf 为 true（认票仍用此比对）。
     */
    @Test
    @Order(2)
    @DisplayName("extracts 写出模板占位路径时可检出")
    void extractsWrite_detectsTemplatePath() {
        List<Map<String, Object>> extracts = List.of(Map.of(
                "name", "token",
                "scope", "asset",
                "entryKey", "clientAuth",
                "fieldPath", "token",
                "expr", "$.data.token",
                "from", "body"
        ));
        Set<String> paths = Set.of("asset.clientauth.token");
        assertTrue(ManagedAuthHeaderApplier.extractsWriteAnyOf(extracts, paths));
        assertFalse(ManagedAuthHeaderApplier.extractsWriteAnyOf(List.of(), paths));
    }
}
