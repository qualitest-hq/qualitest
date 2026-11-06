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
 * 测节点 data 白名单剔除未知键。
 * 边界：http/assert/subflow 合法键保留；臆造键剔除；alwaysRun 与子流 inputs/outputs 可保留。
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

    /**
     * 前提：http data 含 statusCheck（探活白名单）与无关 requestConfig。
     * 期望：保留 statusCheck，剔除 requestConfig。
     */
    @Test
    @Order(3)
    @DisplayName("http 保留 statusCheck、剔除未知键")
    void stripUnknown_http_keepsStatusCheck() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("statusCheck", Map.of("mode", "whitelist", "values", List.of(200, 401)));
        data.put("successCheck", Map.of("mode", "off"));
        data.put("requestConfig", Map.of("method", "GET"));
        List<String> removed = FlowDesignNodeDataKeys.stripUnknown("http", data);
        assertEquals(List.of("requestConfig"), removed);
        assertTrue(data.containsKey("statusCheck"));
        assertTrue(data.containsKey("successCheck"));
        assertFalse(data.containsKey("requestConfig"));
    }

    /**
     * 前提：subflow data 含 inputs/outputs/alwaysRun 与误放的节点级 flowOutputs。
     * 期望：保留映射与 alwaysRun，剔除 flowOutputs。
     */
    @Test
    @Order(4)
    @DisplayName("subflow 保留 inputs/outputs/alwaysRun，剔除节点级 flowOutputs")
    void stripUnknown_subflow_keepsIoAndAlwaysRun() {
        Map<String, Object> data = new HashMap<>();
        data.put("subflowId", "100");
        data.put("inputs", List.of(Map.of("name", "orderId", "value", "{{flow.orderId}}")));
        data.put("outputs", List.of(Map.of("name", "token", "flowKey", "token")));
        data.put("alwaysRun", true);
        data.put("flowOutputs", List.of(Map.of("name", "token")));
        List<String> removed = FlowDesignNodeDataKeys.stripUnknown("subflow", data);
        assertEquals(List.of("flowOutputs"), removed);
        assertTrue(data.containsKey("inputs"));
        assertTrue(data.containsKey("outputs"));
        assertTrue(data.containsKey("alwaysRun"));
        assertFalse(data.containsKey("flowOutputs"));
    }

    /**
     * 前提：http data 含 alwaysRun。
     * 期望：保留 alwaysRun。
     */
    @Test
    @Order(5)
    @DisplayName("http 保留 alwaysRun")
    void stripUnknown_http_keepsAlwaysRun() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "external");
        data.put("alwaysRun", true);
        List<String> removed = FlowDesignNodeDataKeys.stripUnknown("http", data);
        assertTrue(removed.isEmpty());
        assertTrue(data.containsKey("alwaysRun"));
    }
}
