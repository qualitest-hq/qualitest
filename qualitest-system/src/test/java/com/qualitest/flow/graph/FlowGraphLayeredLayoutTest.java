package com.qualitest.flow.graph;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测画布自动排版：链式分层、分支同层、无边换行、森林分量、仅新增节点找位、长链折行。
 * 边界：纯函数改写 position；不依赖 Spring。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowGraphLayeredLayoutTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowGraphLayeredLayoutTest {

    /**
     * 前提：A→B→C 三节点链式边。
     * 期望：层 0/1/2 的 x 递增，y 同为原点。
     */
    @Test
    @Order(1)
    @DisplayName("链式边左到右分层")
    void apply_chain_layersLeftToRight() {
        GraphJson g = graph(
                List.of(node("A"), node("B"), node("C")),
                List.of(edge("e1", "A", "B"), edge("e2", "B", "C")));

        FlowGraphLayeredLayout.apply(g);

        assertEquals(FlowGraphLayeredLayout.ORIGIN_X, xOf(g, "A"), 0.01);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_X + FlowGraphLayeredLayout.LAYER_GAP_X, xOf(g, "B"), 0.01);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_X + 2 * FlowGraphLayeredLayout.LAYER_GAP_X, xOf(g, "C"), 0.01);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_Y, yOf(g, "A"), 0.01);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_Y, yOf(g, "B"), 0.01);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_Y, yOf(g, "C"), 0.01);
    }

    /**
     * 前提：R→A、R→B 两分支。
     * 期望：R 在层 0；A、B 同层且 y 不同。
     */
    @Test
    @Order(2)
    @DisplayName("分支同层上下错开")
    void apply_branch_sameLayerDifferentY() {
        GraphJson g = graph(
                List.of(node("R"), node("A"), node("B")),
                List.of(edge("e1", "R", "A"), edge("e2", "R", "B")));

        FlowGraphLayeredLayout.apply(g);

        assertEquals(FlowGraphLayeredLayout.ORIGIN_X, xOf(g, "R"), 0.01);
        assertEquals(xOf(g, "A"), xOf(g, "B"), 0.01);
        assertTrue(Math.abs(yOf(g, "A") - yOf(g, "B")) >= FlowGraphLayeredLayout.NODE_GAP_Y - 0.01);
    }

    /**
     * 前提：5 个孤立节点、无边。
     * 期望：第 5 个折到第二行；前 4 个同行不同列。
     */
    @Test
    @Order(3)
    @DisplayName("无边时按列换行")
    void apply_noEdges_wrapsByColumns() {
        List<GraphNode> nodes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            nodes.add(node("N" + i));
        }
        GraphJson g = graph(nodes, List.of());

        FlowGraphLayeredLayout.apply(g);

        assertEquals(FlowGraphLayeredLayout.ORIGIN_Y, yOf(g, "N0"), 0.01);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_Y, yOf(g, "N3"), 0.01);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_Y + FlowGraphLayeredLayout.NODE_GAP_Y, yOf(g, "N4"), 0.01);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_X, xOf(g, "N4"), 0.01);
    }

    /**
     * 前提：两段互不连通的链 A→B 与 C→D。
     * 期望：第二分量整体 y 更大。
     */
    @Test
    @Order(4)
    @DisplayName("森林分量纵向错开")
    void apply_forest_componentsOffsetY() {
        GraphJson g = graph(
                List.of(node("A"), node("B"), node("C"), node("D")),
                List.of(edge("e1", "A", "B"), edge("e2", "C", "D")));

        FlowGraphLayeredLayout.apply(g);

        assertTrue(yOf(g, "C") > yOf(g, "A"));
        assertEquals(xOf(g, "A"), xOf(g, "C"), 0.01);
        assertEquals(xOf(g, "B"), xOf(g, "D"), 0.01);
    }

    /**
     * 前提：固定节点 A 在 (40,80)，新增 B，边 A→B。
     * 期望：仅 B 被移动到 A 右侧；A 坐标不变。
     */
    @Test
    @Order(6)
    @DisplayName("placeNewNodes 只动新增节点")
    void placeNewNodes_onlyMovesNew() {
        GraphNode a = node("A");
        a.setPosition(GraphNodePosition.builder().x(40).y(80).build());
        GraphNode b = node("B");
        GraphJson g = graph(
                List.of(a, b),
                List.of(edge("e1", "A", "B")));

        FlowGraphLayeredLayout.placeNewNodes(g, List.of("B"));

        assertEquals(40, xOf(g, "A"), 0.01);
        assertEquals(80, yOf(g, "A"), 0.01);
        assertEquals(FlowGraphLayeredLayout.ORIGIN_X + FlowGraphLayeredLayout.LAYER_GAP_X, xOf(g, "B"), 0.01);
        assertEquals(80, yOf(g, "B"), 0.01);
    }

    /**
     * 前提：A→B→C→D→E→F 六节点长链。
     * 期望：按列折成多行带；第 4 个节点（D）回到左侧下一行带，整图宽度不超过单行带最大列数。
     */
    @Test
    @Order(7)
    @DisplayName("长链按列折行兼顾宽高")
    void apply_longChain_wrapsIntoBands() {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        String[] ids = {"A", "B", "C", "D", "E", "F"};
        for (String id : ids) {
            nodes.add(node(id));
        }
        for (int i = 0; i < ids.length - 1; i++) {
            edges.add(edge("e" + i, ids[i], ids[i + 1]));
        }
        GraphJson g = graph(nodes, edges);

        FlowGraphLayeredLayout.apply(g);

        int cols = FlowGraphLayeredLayout.resolveLayerColumns(6, 6);
        assertEquals(3, cols);
        // D 为第 4 层（layer=3）→ 折到第二行带第 0 列
        assertEquals(FlowGraphLayeredLayout.ORIGIN_X, xOf(g, "D"), 0.01);
        assertTrue(yOf(g, "D") > yOf(g, "A"));
        // 整图宽度不超过单行带最大列数
        double maxX = g.getNodes().stream()
                .mapToDouble(n -> n.getPosition().getX())
                .max()
                .orElse(0);
        assertTrue(maxX <= FlowGraphLayeredLayout.ORIGIN_X
                + (FlowGraphLayeredLayout.MAX_LAYER_COLUMNS - 1) * FlowGraphLayeredLayout.LAYER_GAP_X
                + 0.01);
    }

    /**
     * 前提：条件节点（2 分支）→ HTTP；条件卡更宽更高。
     * 期望：下游 x = 条件卡右缘 + 水平空隙，不再按标准 300 宽步进。
     */
    @Test
    @Order(8)
    @DisplayName("条件节点按更宽占位拉开下游")
    void apply_conditionNode_usesWiderSize() {
        GraphNode cond = GraphNode.builder()
                .id("C")
                .type("condition")
                .data(new java.util.HashMap<>(java.util.Map.of(
                        "branches", java.util.List.of(
                                java.util.Map.of("id", "b1"),
                                java.util.Map.of("id", "b2")))))
                .position(GraphNodePosition.builder().x(0).y(0).build())
                .build();
        GraphNode http = node("H");
        GraphJson g = graph(List.of(cond, http), List.of(edge("e1", "C", "H")));

        FlowGraphLayeredLayout.apply(g);

        assertEquals(FlowGraphLayeredLayout.ORIGIN_X, xOf(g, "C"), 0.01);
        assertEquals(
                FlowGraphLayeredLayout.ORIGIN_X + FlowGraphLayeredLayout.COND_NODE_W + FlowGraphLayeredLayout.GAP_X,
                xOf(g, "H"),
                0.01);
        assertTrue(xOf(g, "H") > FlowGraphLayeredLayout.ORIGIN_X + FlowGraphLayeredLayout.LAYER_GAP_X - 0.01);
    }

    /**
     * 前提：空图或 null。
     * 期望：不抛异常，null 原样返回。
     */
    @Test
    @Order(5)
    @DisplayName("空图安全")
    void apply_empty_safe() {
        assertEquals(null, FlowGraphLayeredLayout.apply(null));
        GraphJson empty = GraphJson.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build();
        assertNotNull(FlowGraphLayeredLayout.apply(empty));
    }

    private static GraphJson graph(List<GraphNode> nodes, List<GraphEdge> edges) {
        return GraphJson.builder()
                .nodes(new ArrayList<>(nodes))
                .edges(new ArrayList<>(edges))
                .build();
    }

    private static GraphNode node(String id) {
        return GraphNode.builder()
                .id(id)
                .type("http")
                .position(GraphNodePosition.builder().x(0).y(0).build())
                .build();
    }

    private static GraphEdge edge(String id, String source, String target) {
        return GraphEdge.builder().id(id).source(source).target(target).build();
    }

    private static double xOf(GraphJson g, String id) {
        return g.getNodes().stream().filter(n -> id.equals(n.getId())).findFirst().orElseThrow().getPosition().getX();
    }

    private static double yOf(GraphJson g, String id) {
        return g.getNodes().stream().filter(n -> id.equals(n.getId())).findFirst().orElseThrow().getPosition().getY();
    }
}
