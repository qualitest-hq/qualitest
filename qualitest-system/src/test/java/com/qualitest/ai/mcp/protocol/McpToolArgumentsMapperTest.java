package com.qualitest.ai.mcp.protocol;

import com.qualitest.api.params.McpToolInvokeParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.common.test.FlowTestSections.begin;
import static com.qualitest.common.test.FlowTestSections.end;
import static com.qualitest.common.test.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link McpToolArgumentsMapper} 单元测试。
 * <p>
 * 验证 MCP tools/call 的 arguments 映射：信封字段（testFlowId、scopeApiIds、graphJson）
 * 与业务参数字段分离，null 输入返回空 {@link McpToolInvokeParams}。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=McpToolArgumentsMapperTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpToolArgumentsMapperTest {

    private McpToolArgumentsMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new McpToolArgumentsMapper();
    }

    /**
     * arguments 同时含信封字段与业务字段（testFlowId、scopeApiIds、keyword、limit）。
     * 期望：testFlowId=1001L；scopeApiIds=["1","2"]；arguments 仅含 keyword、limit。
     */
    @Test
    @Order(1)
    void fromToolArguments_splitsEnvelopeAndBusinessArgs() {
        begin("fromToolArguments_splitsEnvelopeAndBusinessArgs");
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
        log("testFlowId=1001 scopeApiIds=2 businessArgs=2");
        end("fromToolArguments_splitsEnvelopeAndBusinessArgs");
    }

    /**
     * arguments 含 graphJson 对象（nodes/edges 为空数组）。
     * 期望：{@link McpToolInvokeParams#getGraphJson()} 非空且 nodes 为空。
     */
    @Test
    @Order(2)
    void fromToolArguments_parsesGraphJson() {
        begin("fromToolArguments_parsesGraphJson");
        Map<String, Object> graphJson = Map.of(
                "nodes", List.of(),
                "edges", List.of());
        McpToolInvokeParams params = mapper.fromToolArguments(Map.of("graphJson", graphJson));

        assertNotNull(params.getGraphJson());
        assertTrue(params.getGraphJson().getNodes().isEmpty());
        log("graphJsonNodes=0");
        end("fromToolArguments_parsesGraphJson");
    }

    /**
     * arguments 为 null。
     * 期望：返回空 {@link McpToolInvokeParams}；testProjectId 与 arguments 均为 null；不抛异常。
     */
    @Test
    @Order(3)
    void fromToolArguments_null_returnsEmptyParams() {
        begin("fromToolArguments_null_returnsEmptyParams");
        McpToolInvokeParams params = mapper.fromToolArguments(null);

        assertNull(params.getTestProjectId());
        assertNull(params.getArguments());
        log("emptyParams=true");
        end("fromToolArguments_null_returnsEmptyParams");
    }
}
