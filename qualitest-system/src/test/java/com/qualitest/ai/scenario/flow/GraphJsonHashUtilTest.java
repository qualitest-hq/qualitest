package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 测 GraphJsonHashUtil：图结构稳定哈希（节点顺序无关、边变更敏感）。
 * 边界：纯函数，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=GraphJsonHashUtilTest
 */
class GraphJsonHashUtilTest {

    /**
     * 前提：同一图对象与 JSON 深拷贝后再算哈希。
     * 期望：哈希非空、长度 16，且两次相等。
     */
    @Test
    void computeBaseGraphHash_sameGraph_producesSameHash() {
        GraphJson graph = sampleGraph();
        String hash1 = GraphJsonHashUtil.computeBaseGraphHash(graph);
        String hash2 = GraphJsonHashUtil.computeBaseGraphHash(JSON.parseObject(JSON.toJSONString(graph), GraphJson.class));
        assertNotNull(hash1);
        assertEquals(16, hash1.length());
        assertEquals(hash1, hash2);
    }

    /**
     * 前提：两图节点集合相同但列表顺序不同。
     * 期望：哈希相同。
     */
    @Test
    void computeBaseGraphHash_nodeOrderIgnored() {
        GraphJson graphA = sampleGraph();
        GraphJson graphB = JSON.parseObject(JSON.toJSONString(graphA), GraphJson.class);
        GraphNode first = graphB.getNodes().remove(0);
        graphB.getNodes().add(first);

        assertEquals(
                GraphJsonHashUtil.computeBaseGraphHash(graphA),
                GraphJsonHashUtil.computeBaseGraphHash(graphB));
    }

    /**
     * 前提：仅改一条边的 target。
     * 期望：哈希不同。
     */
    @Test
    void computeBaseGraphHash_changedEdge_producesDifferentHash() {
        GraphJson base = sampleGraph();
        GraphJson changed = JSON.parseObject(JSON.toJSONString(base), GraphJson.class);
        changed.getEdges().get(0).setTarget("9999");

        assertNotEquals(
                GraphJsonHashUtil.computeBaseGraphHash(base),
                GraphJsonHashUtil.computeBaseGraphHash(changed));
    }

    /**
     * 前提：图为 null 或空 builder。
     * 期望：返回稳定非空哈希，且两者相等。
     */
    @Test
    void computeBaseGraphHash_nullGraph_returnsStableHash() {
        String hash = GraphJsonHashUtil.computeBaseGraphHash(null);
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertEquals(hash, GraphJsonHashUtil.computeBaseGraphHash(GraphJson.builder().build()));
    }

    private static GraphJson sampleGraph() {
        GraphNode nodeA = GraphNode.builder()
                .id("1001")
                .type("http")
                .build();
        GraphNode nodeB = GraphNode.builder()
                .id("1002")
                .type("http")
                .build();
        GraphEdge edge = GraphEdge.builder()
                .id("8001")
                .source("1001")
                .target("1002")
                .build();
        return GraphJson.builder()
                .nodes(java.util.List.of(nodeB, nodeA))
                .edges(java.util.List.of(edge))
                .build();
    }
}
