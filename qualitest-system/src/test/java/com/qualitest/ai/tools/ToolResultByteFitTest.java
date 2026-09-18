package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：工具结果按形状裁字节上限。
 * 边界：超限减条/清空数组后仍保留原形状键；不能变成只有 truncated/hint 的空壳。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ToolResultByteFitTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolResultByteFitTest {

    /**
     * 前提：20 条长 path 的 items，上限 400。
     * 期望：至少留 1 条；truncated=true。
     */
    @Test
    @Order(1)
    @DisplayName("fitItemsList：超限时从尾部裁剪，至少保留 1 条")
    void fitItemsList_keepsAtLeastOne() {
        JSONArray items = new JSONArray();
        for (int i = 0; i < 20; i++) {
            JSONObject row = new JSONObject();
            row.put("id", String.valueOf(i));
            row.put("path", "/api/" + "x".repeat(80) + i);
            items.add(row);
        }
        JSONObject result = new JSONObject();
        result.put("items", items);
        result.put("truncated", false);

        String out = ToolResultByteFit.fitItemsList(result, 400);
        JSONObject parsed = JSON.parseObject(out);

        assertTrue(out.getBytes(StandardCharsets.UTF_8).length <= 400
                || parsed.getJSONArray("items").size() == 1);
        assertTrue(parsed.getJSONArray("items").size() >= 1);
        assertTrue(parsed.getBooleanValue("truncated"));
    }

    /**
     * 前提：3 个长 name 节点 + 2 条边，上限 280。
     * 期望：edges 仍是空数组键；nodes 至少 1 条；nodeCount=3；不超过上限。
     */
    @Test
    @Order(2)
    @DisplayName("fitGraphTopology：优先清空 edges，保留 nodeCount 与数组键")
    void fitGraphTopology_dropsEdgesFirst() {
        JSONObject result = sampleGraph(3, 100);

        String out = ToolResultByteFit.fitGraphTopology(result, 280);
        JSONObject parsed = JSON.parseObject(out);

        assertNotNull(parsed.getJSONArray("edges"));
        assertEquals(0, parsed.getJSONArray("edges").size());
        assertEquals(3, parsed.getIntValue("nodeCount"));
        assertNotNull(parsed.getJSONArray("nodes"));
        assertTrue(parsed.getJSONArray("nodes").size() >= 1);
        assertTrue(out.getBytes(StandardCharsets.UTF_8).length <= 280);
    }

    /**
     * 前提：单节点 name 很长，上限很紧。
     * 期望：仍有 edges/nodes 键；不能只剩 truncated/hint。
     */
    @Test
    @Order(3)
    @DisplayName("fitGraphTopology：结构裁完仍超限也不换形状")
    void fitGraphTopology_stillOverKeepsShape() {
        JSONObject result = new JSONObject();
        result.put("nodeCount", 1);
        result.put("edgeCount", 0);
        JSONObject node = new JSONObject();
        node.put("id", "n0");
        node.put("name", "node-" + "y".repeat(400));
        JSONArray nodes = new JSONArray();
        nodes.add(node);
        result.put("nodes", nodes);
        result.put("edges", new JSONArray());

        String out = ToolResultByteFit.fitGraphTopology(result, 120);
        JSONObject parsed = JSON.parseObject(out);

        assertTrue(parsed.containsKey("edges"));
        assertTrue(parsed.containsKey("nodes"));
        assertNotNull(parsed.getJSONArray("edges"));
        assertNotNull(parsed.getJSONArray("nodes"));
        assertEquals(1, parsed.getJSONArray("nodes").size());
        assertEquals("n0", parsed.getJSONArray("nodes").getJSONObject(0).getString("id"));
        assertEquals(1, parsed.getIntValue("nodeCount"));
        assertTrue(out.getBytes(StandardCharsets.UTF_8).length <= 120);
    }

    /**
     * 前提：graphJson 很大。
     * 期望：去掉 graphJson，保留 testFlowId。
     */
    @Test
    @Order(4)
    @DisplayName("fitFlowRecord：超限时去掉 graphJson，保留元数据")
    void fitFlowRecord_dropsGraphJson() {
        JSONObject result = new JSONObject();
        result.put("testFlowId", "1");
        result.put("flowName", "demo");
        result.put("graphJson", JSON.parseObject("{\"nodes\":[{\"id\":\"a\",\"data\":{\"pad\":\""
                + "z".repeat(500) + "\"}}]}"));

        String out = ToolResultByteFit.fitFlowRecord(result, 200);
        JSONObject parsed = JSON.parseObject(out);

        assertFalse(parsed.containsKey("graphJson"));
        assertEquals("1", parsed.getString("testFlowId"));
        assertTrue(parsed.getBooleanValue("truncated"));
    }

    /**
     * 前提：failures 与分区数组重复。
     * 期望：去掉分区数组，failures 至少 1 条。
     */
    @Test
    @Order(5)
    @DisplayName("fitRunFailure：去掉分区数组，保留 failures")
    void fitRunFailure_dropsPartitionArrays() {
        JSONArray failures = new JSONArray();
        for (int i = 0; i < 5; i++) {
            JSONObject f = new JSONObject();
            f.put("nodeId", "n" + i);
            f.put("stepDetails", "detail-" + "d".repeat(200));
            failures.add(f);
        }
        JSONObject result = new JSONObject();
        result.put("failed", true);
        result.put("failureCount", 5);
        result.put("failures", failures);
        result.put("bizCodeFailures", failures);
        result.put("assertFailures", new JSONArray());
        result.put("otherFailures", new JSONArray());

        String out = ToolResultByteFit.fitRunFailure(result, 500);
        JSONObject parsed = JSON.parseObject(out);

        assertFalse(parsed.containsKey("bizCodeFailures"));
        assertTrue(parsed.containsKey("failures"));
        assertTrue(parsed.getJSONArray("failures").size() >= 1);
    }

    /**
     * 前提：含超长 mermaid + 节点边，上限偏紧。
     * 期望：先去掉 mermaid；仍保留 nodes/edges 键。
     */
    @Test
    @Order(6)
    @DisplayName("fitGraphTopology：超限先删 mermaid")
    void fitGraphTopology_dropsMermaidFirst() {
        JSONObject result = sampleGraph(2, 20);
        result.put("mermaid", "flowchart TB\n  " + "n0[\"HTTP · " + "x".repeat(200) + "\"]");

        String out = ToolResultByteFit.fitGraphTopology(result, 220);
        JSONObject parsed = JSON.parseObject(out);

        assertFalse(parsed.containsKey("mermaid"));
        assertTrue(parsed.getBooleanValue("truncated"));
        assertTrue(parsed.containsKey("nodes"));
        assertTrue(parsed.containsKey("edges"));
        assertTrue(out.getBytes(StandardCharsets.UTF_8).length <= 220);
    }

    private static JSONObject sampleGraph(int nodeCount, int nameRepeat) {
        JSONObject result = new JSONObject();
        result.put("nodeCount", nodeCount);
        result.put("edgeCount", 2);
        JSONArray nodes = new JSONArray();
        for (int i = 0; i < nodeCount; i++) {
            JSONObject n = new JSONObject();
            n.put("id", "n" + i);
            n.put("name", "node-" + "y".repeat(nameRepeat));
            nodes.add(n);
        }
        result.put("nodes", nodes);
        JSONArray edges = new JSONArray();
        edges.add(JSON.parseObject("{\"id\":\"e1\",\"source\":\"n0\",\"target\":\"n1\"}"));
        edges.add(JSON.parseObject("{\"id\":\"e2\",\"source\":\"n1\",\"target\":\"n2\"}"));
        result.put("edges", edges);
        return result;
    }
}
