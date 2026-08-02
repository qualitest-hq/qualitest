package com.qualitest.flow.validate;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectApi;
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
 * 测设计期断言路径门禁：用接口响应示例试算 http.body 左值。
 * 覆盖：误写 data.items 报错、正确过滤器通过、无示例跳过、空数组算未命中、condition 读 conditions。
 * 边界：纯内存图与 API，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AssertPathDesignGateTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssertPathDesignGateTest {

    /** 购物车列表接口：data 直接是数组，没有 items 键 */
    private static final String CART_RESPONSE = """
            {"responses":[{"id":"r1","example":{"code":200,"data":[{"cartId":"5001","quantity":3,"subtotal":147}]}}]}
            """;

    @Test
    @Order(1)
    @DisplayName("左值误含 data.items 时试算未命中并报错")
    void validate_rejectsLegacyItemsPath() {
        GraphJson graph = cartFlow("http.body.data.items[?(@.cartId=='5001')].quantity");
        List<String> errors = AssertPathDesignGate.validate(graph, id -> cartApi());
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains(".items") || errors.get(0).contains("未命中"));
    }

    @Test
    @Order(2)
    @DisplayName("正确的 data[?] 过滤器路径通过门禁")
    void validate_acceptsArrayFilterPath() {
        GraphJson graph = cartFlow("http.body.data[?(@.cartId=='5001')].quantity");
        List<String> errors = AssertPathDesignGate.validate(graph, id -> cartApi());
        assertEquals(List.of(), errors);
    }

    @Test
    @Order(3)
    @DisplayName("接口无响应示例且无 schema 时跳过门禁")
    void validate_skipsWhenNoExample() {
        GraphJson graph = cartFlow("http.body.data.items[0].quantity");
        List<String> errors = AssertPathDesignGate.validate(graph, id -> TestProjectApi.builder()
                .testProjectApiId(1L)
                .responseConfig("{\"responses\":[{}]}")
                .build());
        assertEquals(List.of(), errors);
    }

    @Test
    @Order(4)
    @DisplayName("试算得到空数组视为未命中")
    void isMiss_emptyList() {
        Object body = Map.of("data", List.of(Map.of("cartId", "5001", "quantity", 3)));
        Object bad = AssertPathDesignGate.evalAssertLeft(body, "http.body.data.items[?(@.cartId=='5001')].quantity");
        assertTrue(AssertPathDesignGate.isMiss(bad));
        Object good = AssertPathDesignGate.evalAssertLeft(body, "http.body.data[?(@.cartId=='5001')].quantity");
        assertFalse(AssertPathDesignGate.isMiss(good));
    }

    @Test
    @Order(5)
    @DisplayName("condition 节点检查 branches[].conditions 左值")
    void validate_conditionUsesConditionsField() {
        Map<String, Object> httpData = new HashMap<>();
        httpData.put("callMode", "project");
        httpData.put("testProjectApiId", "1");

        Map<String, Object> branch = new HashMap<>();
        branch.put("id", "if-1");
        branch.put("kind", "if");
        branch.put("conditions", List.of(Map.of(
                "left", "http.body.data.items[0].quantity",
                "operator", "eq",
                "right", "3")));

        Map<String, Object> condData = new HashMap<>();
        condData.put("name", "分支");
        condData.put("branches", List.of(branch));

        GraphJson graph = GraphJson.builder()
                .nodes(List.of(
                        GraphNode.builder().id("h1").type("http").data(httpData).build(),
                        GraphNode.builder().id("c1").type("condition").data(condData).build()))
                .edges(List.of(GraphEdge.builder().id("e1").source("h1").target("c1").build()))
                .build();

        List<String> errors = AssertPathDesignGate.validate(graph, id -> cartApi());
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("条件节点"));
        assertTrue(errors.get(0).contains("conditions[0]"));
    }

    private static TestProjectApi cartApi() {
        return TestProjectApi.builder()
                .testProjectApiId(1L)
                .responseConfig(CART_RESPONSE)
                .build();
    }

    /** 构造 HTTP → assert 两节点线性图，断言左值由参数指定 */
    private static GraphJson cartFlow(String assertLeft) {
        Map<String, Object> httpData = new HashMap<>();
        httpData.put("name", "我的购物车");
        httpData.put("callMode", "project");
        httpData.put("testProjectApiId", "1");

        Map<String, Object> rule = new HashMap<>();
        rule.put("left", assertLeft);
        rule.put("operator", "eq");
        rule.put("right", "3");

        Map<String, Object> assertData = new HashMap<>();
        assertData.put("name", "断言数量");
        assertData.put("rules", List.of(rule));

        return GraphJson.builder()
                .nodes(List.of(
                        GraphNode.builder().id("h1").type("http").data(httpData).build(),
                        GraphNode.builder().id("a1").type("assert").data(assertData).build()))
                .edges(List.of(GraphEdge.builder().id("e1").source("h1").target("a1").build()))
                .build();
    }
}
