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

class GraphJsonHashUtilTest {

    @Test
    void computeBaseGraphHash_sameGraph_producesSameHash() {
        GraphJson graph = sampleGraph();
        String hash1 = GraphJsonHashUtil.computeBaseGraphHash(graph);
        String hash2 = GraphJsonHashUtil.computeBaseGraphHash(JSON.parseObject(JSON.toJSONString(graph), GraphJson.class));
        assertNotNull(hash1);
        assertEquals(16, hash1.length());
        assertEquals(hash1, hash2);
    }

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

    @Test
    void computeBaseGraphHash_changedEdge_producesDifferentHash() {
        GraphJson base = sampleGraph();
        GraphJson changed = JSON.parseObject(JSON.toJSONString(base), GraphJson.class);
        changed.getEdges().get(0).setTarget("9999");

        assertNotEquals(
                GraphJsonHashUtil.computeBaseGraphHash(base),
                GraphJsonHashUtil.computeBaseGraphHash(changed));
    }

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
