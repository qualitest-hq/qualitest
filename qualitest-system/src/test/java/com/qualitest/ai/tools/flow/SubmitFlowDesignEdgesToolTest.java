package com.qualitest.ai.tools.flow;

import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.flow.model.GraphEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测谁：SubmitFlowDesignEdgesTool.upsertEdges（边参数组装）。
 * 边界：纯内存参数拼装，无 Spring、不做图校验；覆盖串链、单边、去重、空参报错。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SubmitFlowDesignEdgesToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubmitFlowDesignEdgesToolTest {

    /**
     * 前提：仅 chainNodeIds=[a,b,c]。
     * 期望：生成 a→b、b→c 两条 add 边。
     */
    @Test
    @Order(1)
    @DisplayName("chainNodeIds 按序串链")
    void upsertEdges_chainNodeIds_buildsAdjacentAdds() {
        Map<String, Object> args = new HashMap<>();
        args.put("chainNodeIds", List.of("a", "b", "c"));

        FlowDesignPatch patch = SubmitFlowDesignEdgesTool.upsertEdges(args);

        assertEquals(2, patch.getAddEdges().size());
        assertEquals(0, patch.getUpdateEdges().size());
        assertEquals("a", patch.getAddEdges().get(0).getSource());
        assertEquals("b", patch.getAddEdges().get(0).getTarget());
        assertEquals("b", patch.getAddEdges().get(1).getSource());
        assertEquals("c", patch.getAddEdges().get(1).getTarget());
    }

    /**
     * 前提：edges 长度 1，op=add。
     * 期望：一条 addEdges。
     */
    @Test
    @Order(2)
    @DisplayName("edges 长度 1")
    void upsertEdges_singleEdge_buildsOneAdd() {
        Map<String, Object> edge = new HashMap<>();
        edge.put("op", "add");
        edge.put("source", "n1");
        edge.put("target", "n2");
        edge.put("label", "if");
        Map<String, Object> args = new HashMap<>();
        args.put("edges", List.of(edge));

        FlowDesignPatch patch = SubmitFlowDesignEdgesTool.upsertEdges(args);

        assertEquals(1, patch.getAddEdges().size());
        GraphEdge e = patch.getAddEdges().get(0);
        assertEquals("n1", e.getSource());
        assertEquals("n2", e.getTarget());
        assertEquals("if", e.getLabel());
        assertNotNull(e.getId());
        assertFalse(e.getId().isBlank());
    }

    /**
     * 前提：chain 含 a→b，edges 也显式传 a→b。
     * 期望：串链该项被去重，最终仅一条 a→b（来自 edges）。
     */
    @Test
    @Order(3)
    @DisplayName("chain 与 edges 同 pair 去重")
    void upsertEdges_chainDedupsAgainstExplicitEdges() {
        Map<String, Object> edge = new HashMap<>();
        edge.put("op", "add");
        edge.put("id", "e_explicit");
        edge.put("source", "a");
        edge.put("target", "b");
        Map<String, Object> args = new HashMap<>();
        args.put("chainNodeIds", List.of("a", "b", "c"));
        args.put("edges", List.of(edge));

        FlowDesignPatch patch = SubmitFlowDesignEdgesTool.upsertEdges(args);

        assertEquals(2, patch.getAddEdges().size());
        long ab = patch.getAddEdges().stream()
                .filter(e -> "a".equals(e.getSource()) && "b".equals(e.getTarget()))
                .count();
        assertEquals(1, ab);
        assertTrue(patch.getAddEdges().stream().anyMatch(e -> "e_explicit".equals(e.getId())));
        assertTrue(patch.getAddEdges().stream()
                .anyMatch(e -> "b".equals(e.getSource()) && "c".equals(e.getTarget())));
    }

    /**
     * 前提：未传 edges 且 chainNodeIds 不足 2。
     * 期望：抛 IllegalArgumentException。
     */
    @Test
    @Order(4)
    @DisplayName("空参数报错")
    void upsertEdges_empty_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> SubmitFlowDesignEdgesTool.upsertEdges(Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> SubmitFlowDesignEdgesTool.upsertEdges(Map.of("chainNodeIds", List.of("only"))));
    }
}
