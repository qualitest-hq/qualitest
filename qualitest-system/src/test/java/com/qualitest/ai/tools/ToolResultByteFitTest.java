package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultByteFitTest {

    @Test
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

    @Test
    @DisplayName("fitGraphTopology：优先清空 edges，保留 nodeCount")
    void fitGraphTopology_dropsEdgesFirst() {
        JSONObject result = new JSONObject();
        result.put("nodeCount", 3);
        result.put("edgeCount", 2);
        JSONArray nodes = new JSONArray();
        for (int i = 0; i < 3; i++) {
            JSONObject n = new JSONObject();
            n.put("id", "n" + i);
            n.put("name", "node-" + "y".repeat(100));
            nodes.add(n);
        }
        result.put("nodes", nodes);
        JSONArray edges = new JSONArray();
        edges.add(JSON.parseObject("{\"id\":\"e1\",\"source\":\"n0\",\"target\":\"n1\"}"));
        edges.add(JSON.parseObject("{\"id\":\"e2\",\"source\":\"n1\",\"target\":\"n2\"}"));
        result.put("edges", edges);

        String out = ToolResultByteFit.fitGraphTopology(result, 280);
        JSONObject parsed = JSON.parseObject(out);
        assertEquals(0, parsed.getJSONArray("edges").size());
        assertEquals(3, parsed.getIntValue("nodeCount"));
        assertTrue(parsed.getJSONArray("nodes").size() >= 1);
    }

    @Test
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

    @Test
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
}
