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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测设计期断言路径门禁：按响应 schema 校验 http.body 左值（example 不参与硬拦）。
 * 覆盖：误写 .items、过滤器路径、占位 example、无 schema 跳过、condition、scoped。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AssertPathDesignGateTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssertPathDesignGateTest {

    /** 含 schema + 占位 example（cartId=0）：过滤器应对 schema 通过，不因 example 空命中而失败 */
    private static final String CART_RESPONSE = """
            {"responses":[{"id":"r1","schema":{"type":"object","properties":{"code":{"type":"integer"},"data":{"type":"array","items":{"type":"object","properties":{"cartId":{"type":"integer"},"quantity":{"type":"integer"},"subtotal":{"type":"number"}}}}}},"example":{"code":0,"data":[{"cartId":0,"quantity":0,"subtotal":0}]}}]}
            """;

    @Test
    @Order(1)
    @DisplayName("左值误含 data.items 时硬拦")
    void validate_rejectsLegacyItemsPath() {
        GraphJson graph = cartFlow("http.body.data.items[?(@.cartId=='5001')].quantity");
        List<String> errors = AssertPathDesignGate.validate(graph, id -> cartApi());
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains(".items"));
    }

    @Test
    @Order(2)
    @DisplayName("正确的 data[?] 过滤器路径通过门禁（不因占位 example 失败）")
    void validate_acceptsArrayFilterPathDespitePlaceholderExample() {
        GraphJson graph = cartFlow("http.body.data[?(@.cartId=='5001')].quantity");
        List<String> errors = AssertPathDesignGate.validate(graph, id -> cartApi());
        assertEquals(List.of(), errors);
    }

    @Test
    @Order(3)
    @DisplayName("接口无 schema 时跳过门禁")
    void validate_skipsWhenNoSchema() {
        GraphJson graph = cartFlow("http.body.data.missing.field");
        List<String> errors = AssertPathDesignGate.validate(graph, id -> TestProjectApi.builder()
                .testProjectApiId(1L)
                .responseConfig("{\"responses\":[{\"example\":{\"code\":0}}]}")
                .build());
        assertEquals(List.of(), errors);
    }

    @Test
    @Order(4)
    @DisplayName("软试算：空数组算未命中（属性面板用，非门禁）")
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
        assertTrue(errors.get(0).contains(".items"));
    }

    @Test
    @Order(6)
    @DisplayName("scoped 校验只报指定节点；其它坏断言不影响")
    void validate_scopedOnlyReportsTargetNode() {
        Map<String, Object> httpData = new HashMap<>();
        httpData.put("callMode", "project");
        httpData.put("testProjectApiId", "1");

        Map<String, Object> badAssert = new HashMap<>();
        badAssert.put("name", "坏断言");
        badAssert.put("rules", List.of(Map.of(
                "left", "http.body.data.items[0].quantity",
                "operator", "eq",
                "right", "3")));

        Map<String, Object> goodAssert = new HashMap<>();
        goodAssert.put("name", "好断言");
        goodAssert.put("rules", List.of(Map.of(
                "left", "http.body.data[?(@.cartId=='5001')].quantity",
                "operator", "eq",
                "right", "3")));

        GraphJson graph = GraphJson.builder()
                .nodes(List.of(
                        GraphNode.builder().id("h1").type("http").data(httpData).build(),
                        GraphNode.builder().id("bad").type("assert").data(badAssert).build(),
                        GraphNode.builder().id("good").type("assert").data(goodAssert).build()))
                .edges(List.of(
                        GraphEdge.builder().id("e1").source("h1").target("bad").build(),
                        GraphEdge.builder().id("e2").source("h1").target("good").build()))
                .build();

        List<String> onlyGood = AssertPathDesignGate.validate(graph, id -> cartApi(), Set.of("good"));
        assertEquals(List.of(), onlyGood);

        List<String> onlyBad = AssertPathDesignGate.validate(graph, id -> cartApi(), Set.of("bad"));
        assertFalse(onlyBad.isEmpty());
        assertTrue(onlyBad.get(0).contains("坏断言"));
    }

    @Test
    @Order(7)
    @DisplayName("scoped 校验无上游时硬拦而非跳过")
    void validate_scopedReportsMissingUpstream() {
        Map<String, Object> assertData = new HashMap<>();
        assertData.put("name", "孤立断言");
        assertData.put("rules", List.of(Map.of(
                "left", "http.body.data[0].quantity",
                "operator", "eq",
                "right", "3")));

        GraphJson graph = GraphJson.builder()
                .nodes(List.of(GraphNode.builder().id("a1").type("assert").data(assertData).build()))
                .edges(List.of())
                .build();

        List<String> fullSkip = AssertPathDesignGate.validate(graph, id -> cartApi());
        assertEquals(List.of(), fullSkip);

        List<String> scoped = AssertPathDesignGate.validate(graph, id -> cartApi(), Set.of("a1"));
        assertFalse(scoped.isEmpty());
        assertTrue(scoped.get(0).contains("尚无上游"));
        assertTrue(scoped.get(0).contains("孤立断言"));
    }

    @Test
    @Order(8)
    @DisplayName("schema 中不存在的字段硬拦")
    void validate_rejectsUnknownField() {
        GraphJson graph = cartFlow("http.body.data[0].notAField");
        List<String> errors = AssertPathDesignGate.validate(graph, id -> cartApi());
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("schema"));
    }

    private static TestProjectApi cartApi() {
        return TestProjectApi.builder()
                .testProjectApiId(1L)
                .responseConfig(CART_RESPONSE)
                .build();
    }

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
