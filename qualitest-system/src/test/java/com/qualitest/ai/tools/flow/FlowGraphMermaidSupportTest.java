package com.qualitest.ai.tools.flow;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：FlowGraphMermaidSupport 将 GraphJson 转为 Mermaid flowchart。
 * 边界：线性边、condition 分支标签、特殊字符转义、子集过滤。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowGraphMermaidSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowGraphMermaidSupportTest {

    /**
     * 前提：HTTP → Assert 两节点一线。
     * 期望：含 flowchart TB、两节点声明、一条无标签边。
     */
    @Test
    @Order(1)
    @DisplayName("线性 HTTP→Assert 生成 flowchart")
    void toMermaid_linearHttpAssert() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(
                        node("n1", "http", Map.of("name", "登录")),
                        node("n2", "assert", Map.of("name", "状态码"))
                ))
                .edges(List.of(edge("e1", "n1", "n2", null)))
                .build();

        String mermaid = FlowGraphMermaidSupport.toMermaid(graph);

        assertTrue(mermaid.startsWith("flowchart TB"));
        assertTrue(mermaid.contains("n1[\"HTTP · 登录\"]"));
        assertTrue(mermaid.contains("n2[\"Assert · 状态码\"]"));
        assertTrue(mermaid.contains("n1 --> n2"));
    }

    /**
     * 前提：condition 双出边无 edge.label，branches 带 name/kind。
     * 期望：菱形节点；边标签来自 branches。
     */
    @Test
    @Order(2)
    @DisplayName("condition 分支从 branches 补标签")
    void toMermaid_conditionBranches() {
        Map<String, Object> ifBranch = new LinkedHashMap<>();
        ifBranch.put("id", "b_if");
        ifBranch.put("kind", "if");
        ifBranch.put("name", "成功");
        ifBranch.put("target", "n_ok");
        Map<String, Object> elseBranch = new LinkedHashMap<>();
        elseBranch.put("id", "b_else");
        elseBranch.put("kind", "else");
        elseBranch.put("target", "n_fail");

        GraphJson graph = GraphJson.builder()
                .nodes(List.of(
                        node("n_cond", "condition", Map.of(
                                "name", "是否成功",
                                "branches", List.of(ifBranch, elseBranch)
                        )),
                        node("n_ok", "http", Map.of("name", "下单")),
                        node("n_fail", "assign", Map.of("name", "标记失败"))
                ))
                .edges(List.of(
                        edge("e1", "n_cond", "n_ok", null),
                        edge("e2", "n_cond", "n_fail", null)
                ))
                .build();

        String mermaid = FlowGraphMermaidSupport.toMermaid(graph);

        assertTrue(mermaid.contains("n_cond{\"Condition · 是否成功\"}"));
        assertTrue(mermaid.contains("n_cond -->|成功| n_ok"));
        assertTrue(mermaid.contains("n_cond -->|else| n_fail"));
    }

    /**
     * 前提：节点 name 含引号与方括号。
     * 期望：标签转义后不破坏形状语法。
     */
    @Test
    @Order(3)
    @DisplayName("特殊字符转义")
    void toMermaid_escapesSpecialChars() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(
                        node("n1", "http", Map.of("name", "get \"user\" [id]")),
                        node("n2", "subflow", Map.of("name", "登录{子流}"))
                ))
                .edges(List.of(edge("e1", "n1", "n2", "a|b")))
                .build();

        String mermaid = FlowGraphMermaidSupport.toMermaid(graph);

        assertTrue(mermaid.contains("n1[\"HTTP · get 'user' (id)\"]"));
        assertTrue(mermaid.contains("n2[[\"Subflow · 登录(子流)\"]]"));
        assertTrue(mermaid.contains("n1 -->|a/b| n2"));
        assertFalse(mermaid.contains("[id]"));
    }

    /**
     * 前提：三节点，include 只含前两。
     * 期望：第三节点与跨子集边不出现。
     */
    @Test
    @Order(4)
    @DisplayName("子集只输出两端都在集合内的边")
    void toMermaid_includeSubsetFiltersEdges() {
        GraphJson graph = GraphJson.builder()
                .nodes(List.of(
                        node("a", "http", Map.of("name", "A")),
                        node("b", "assert", Map.of("name", "B")),
                        node("c", "delay", Map.of("name", "C"))
                ))
                .edges(List.of(
                        edge("e1", "a", "b", null),
                        edge("e2", "b", "c", null)
                ))
                .build();

        String mermaid = FlowGraphMermaidSupport.toMermaid(graph, Set.of("a", "b"));

        assertTrue(mermaid.contains("a[\"HTTP · A\"]"));
        assertTrue(mermaid.contains("b[\"Assert · B\"]"));
        assertFalse(mermaid.contains("c[\"Delay · C\"]"));
        assertTrue(mermaid.contains("a --> b"));
        assertFalse(mermaid.contains("b --> c"));
    }

    /**
     * 前提：空图 / null。
     * 期望：仅 flowchart TB。
     */
    @Test
    @Order(5)
    @DisplayName("空图返回头行")
    void toMermaid_emptyGraph() {
        assertTrue("flowchart TB".equals(FlowGraphMermaidSupport.toMermaid(null)));
        assertTrue("flowchart TB".equals(FlowGraphMermaidSupport.toMermaid(GraphJson.builder().build())));
    }

    private static GraphNode node(String id, String type, Map<String, Object> data) {
        return GraphNode.builder().id(id).type(type).data(new LinkedHashMap<>(data)).build();
    }

    private static GraphEdge edge(String id, String source, String target, String label) {
        return GraphEdge.builder().id(id).source(source).target(target).label(label).build();
    }
}
