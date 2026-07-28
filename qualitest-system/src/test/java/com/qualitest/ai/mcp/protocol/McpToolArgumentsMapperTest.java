package com.qualitest.ai.mcp.protocol;

import com.qualitest.api.params.McpToolInvokeParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 McpToolArgumentsMapper：MCP tools/call arguments 拆信封字段与业务参数。
 * 边界：纯映射，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=McpToolArgumentsMapperTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpToolArgumentsMapperTest {

    private McpToolArgumentsMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new McpToolArgumentsMapper();
    }

    /**
     * 前提：arguments 同时含 testFlowId/scopeApiIds 与 keyword/limit。
     * 期望：信封字段进 params；arguments 仅留业务字段。
     */
    @Test
    @Order(1)
    @DisplayName("拆分信封字段与业务参数")
    void fromToolArguments_splitsEnvelopeAndBusinessArgs() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("testFlowId", "1001");
        raw.put("scopeApiIds", List.of("1", "2"));
        raw.put("keyword", "login");
        raw.put("limit", 10);

        McpToolInvokeParams params = mapper.fromToolArguments(raw);

        assertEquals(1001L, params.getTestFlowId());
        assertEquals(List.of("1", "2"), params.getScopeApiIds());
        assertEquals("login", params.getArguments().get("keyword"));
        assertEquals(10, params.getArguments().get("limit"));
    }

    /**
     * 前提：arguments 含 graphJson 对象（空 nodes/edges）。
     * 期望：params.graphJson 非空且 nodes 为空。
     */
    @Test
    @Order(2)
    @DisplayName("解析 graphJson 对象")
    void fromToolArguments_parsesGraphJson() {
        Map<String, Object> graphJson = Map.of(
                "nodes", List.of(),
                "edges", List.of());
        McpToolInvokeParams params = mapper.fromToolArguments(Map.of("graphJson", graphJson));

        assertNotNull(params.getGraphJson());
        assertTrue(params.getGraphJson().getNodes().isEmpty());
    }

    /**
     * 前提：arguments 为 null。
     * 期望：返回空 params，不抛异常。
     */
    @Test
    @Order(3)
    @DisplayName("arguments 为 null 返回空 params")
    void fromToolArguments_null_returnsEmptyParams() {
        McpToolInvokeParams params = mapper.fromToolArguments(null);

        assertNull(params.getTestProjectId());
        assertNull(params.getArguments());
    }
}
