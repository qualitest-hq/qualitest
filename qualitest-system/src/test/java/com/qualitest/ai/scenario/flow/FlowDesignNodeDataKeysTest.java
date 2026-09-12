package com.qualitest.ai.scenario.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 FlowDesignNodeDataKeys：按类型白名单剔除未知 data 键。
 * 边界：http/assert 合法键保留；requestConfig 等幻觉键剔除；未知类型不删。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignNodeDataKeysTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignNodeDataKeysTest {

    /**
     * 前提：http data 含 callMode 与 requestConfig。
     * 期望：剔除 requestConfig，保留 callMode。
     */
    @Test
    @Order(1)
    @DisplayName("http 剔除未知键 requestConfig")
    void stripUnknown_http_removesRequestConfig() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("requestConfig", Map.of("method", "POST"));
        List<String> removed = FlowDesignNodeDataKeys.stripUnknown("http", data);
        assertEquals(List.of("requestConfig"), removed);
        assertTrue(data.containsKey("callMode"));
        assertFalse(data.containsKey("requestConfig"));
    }

    /**
     * 前提：assert data 含 rules 与多余 foo。
     * 期望：剔除 foo。
     */
    @Test
    @Order(2)
    @DisplayName("assert 剔除未知键")
    void stripUnknown_assert_removesExtra() {
        Map<String, Object> data = new HashMap<>();
        data.put("rules", List.of());
        data.put("foo", 1);
        List<String> removed = FlowDesignNodeDataKeys.stripUnknown("assert", data);
        assertEquals(List.of("foo"), removed);
        assertTrue(data.containsKey("rules"));
    }
}
