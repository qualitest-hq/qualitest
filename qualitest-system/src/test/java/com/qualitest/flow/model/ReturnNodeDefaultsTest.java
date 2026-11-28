package com.qualitest.flow.model;

import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 ReturnNodeDefaults：保存期给返回节点补缺省 from（契约 §4.1「保存缺省写 flow.out」）。
 * 边界：纯内存 GraphJson；不落库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ReturnNodeDefaultsTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("返回节点保存期默认值")
class ReturnNodeDefaultsTest {

    /**
     * 前提：http-return 节点没写 data.from。
     * 期望：补成 flow.out，并标记改动。
     */
    @Test
    @Order(1)
    @DisplayName("缺 from 补 flow.out")
    void fillMissingFrom_missing_defaultsToFlowOut() {
        GraphNode node = returnNode("r1", "http-return", null);
        GraphJson graph = GraphJson.builder().nodes(List.of(node)).build();

        boolean changed = ReturnNodeDefaults.fillMissingFrom(graph);

        assertTrue(changed);
        assertEquals("flow.out", node.getData().get("from"));
    }

    /**
     * 前提：subflow-return 的 from 只有空白。
     * 期望：按缺省补 flow.out。
     */
    @Test
    @Order(2)
    @DisplayName("空白 from 也补 flow.out")
    void fillMissingFrom_blank_defaultsToFlowOut() {
        GraphNode node = returnNode("r1", "subflow-return", "   ");
        GraphJson graph = GraphJson.builder().nodes(List.of(node)).build();

        ReturnNodeDefaults.fillMissingFrom(graph);

        assertEquals("flow.out", node.getData().get("from"));
    }

    /**
     * 前提：from 已写好且带两侧空白；另有非返回节点。
     * 期望：只 trim 返回节点；非返回节点不动。
     */
    @Test
    @Order(3)
    @DisplayName("已有 from 只做 trim，非返回节点不动")
    void fillMissingFrom_existingTrimmed() {
        GraphNode ret = returnNode("r1", "http-return", "  flow.pkg  ");
        GraphNode assign = GraphNode.builder().id("a1").type("assign").name("赋值").build();
        GraphJson graph = GraphJson.builder().nodes(List.of(ret, assign)).build();

        boolean changed = ReturnNodeDefaults.fillMissingFrom(graph);

        assertTrue(changed);
        assertEquals("flow.pkg", ret.getData().get("from"));
        assertFalse(assign.getData() != null && assign.getData().containsKey("from"));
    }

    /**
     * 前提：http-return 缺 from，跑全量校验。
     * 期望：补默认后不再报「缺少 from」。
     */
    @Test
    @Order(4)
    @DisplayName("补默认后通过 from 必填校验")
    void fillMissingFrom_makesGraphValidForFrom() {
        String json = """
                {
                  "meta": {"schemaVersion": 2},
                  "nodes": [
                    {"id":"e1","type":"http-entry","name":"入站","position":{"x":0,"y":0},
                     "data":{"method":"POST","path":"/x","envId":"1",
                             "fallbackOut":{"status":500,"headers":{},"body":{}}}},
                    {"id":"r1","type":"http-return","name":"回包","position":{"x":200,"y":0},
                     "data":{}}
                  ],
                  "edges": [{"id":"ed1","source":"e1","target":"r1"}]
                }
                """;
        GraphJson graph = GraphJson.parse(json);
        GraphJsonValidator validator = new GraphJsonValidator(null);
        GraphValidationResult before = validator.validate(graph);
        assertTrue(before.getErrors().stream().anyMatch(e -> e.contains("缺少 from")));

        ReturnNodeDefaults.fillMissingFrom(graph);

        GraphValidationResult after = validator.validate(graph);
        assertFalse(after.getErrors().stream().anyMatch(e -> e.contains("缺少 from")));
        assertNotNull(graph.getNodes().get(1).getData().get("from"));
    }

    /** 返回节点（data 为空 Map / from 可空） */
    private static GraphNode returnNode(String id, String type, String from) {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        if (from != null) {
            data.put("from", from);
        }
        return GraphNode.builder().id(id).type(type).name("返回").data(data).build();
    }
}
