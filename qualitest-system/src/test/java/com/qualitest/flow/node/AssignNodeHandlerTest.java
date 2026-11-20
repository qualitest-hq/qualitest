package com.qualitest.flow.node;


import com.qualitest.flow.run.RunStatus;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.AssignNodeHandler;
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
 * Assign 节点单测：flow 上 set/add，以及结构化 set 写入 out（叶子解析 in 占位）。
 * 不连库。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssignNodeHandlerTest {

    private final AssignNodeHandler handler = new AssignNodeHandler();

    /**
     * 前提：assignments 先 set pollAttempt=0，再 add step=1。
     * 期望：passed；flow.pollAttempt=1；assigns 两条 after 分别为 0、1。
     */
    @Test
    @Order(1)
    @DisplayName("执行：set 后再 add 累加 flow 变量")
    void execute_setAndAdd() {
        JSONArray assignments = new JSONArray();
        assignments.add(row("pollAttempt", "set", "0", null, null));
        assignments.add(row("pollAttempt", "add", null, 1, 0));

        GraphNode node = assignNode(assignments);
        FlowRunContext ctx = new FlowRunContext();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals(1L, ctx.getFlow().get("pollAttempt"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assigns = (List<Map<String, Object>>) result.getAssigns();
        assertEquals(2, assigns.size());
        assertEquals(0L, assigns.get(0).get("after"));
        assertEquals(1L, assigns.get(1).get("after"));
    }

    /**
     * 前提：flow.count=3，assignment 为 add step=2。
     * 期望：flow.count=5；assigns 一条且 op=add。
     */
    @Test
    @Order(2)
    @DisplayName("执行：对已有 flow 值按 step 做 add")
    void execute_addWithStep() {
        JSONArray assignments = new JSONArray();
        assignments.add(row("flow", "count", "add", null, 2, 0));

        GraphNode node = assignNode(assignments);
        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("count", 3);

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals(5L, ctx.getFlow().get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assigns = (List<Map<String, Object>>) result.getAssigns();
        assertEquals(1, assigns.size());
        assertEquals("add", assigns.get(0).get("op"));
        assertEquals(5L, assigns.get(0).get("after"));
    }

    /**
     * 两条 Assign：写 out.status=200、out.body 对象；body.echo 含 {{in.orderId}}。
     * 期望 out 有 status/body，echo 已替换，flow 仍为空。
     */
    @Test
    @Order(3)
    @DisplayName("执行：结构化 set 写入 out.body，叶子占位解析 in")
    void execute_structuredSetToOut() {
        JSONObject body = new JSONObject();
        body.put("code", "SUCCESS");
        body.put("echo", "{{in.orderId}}");

        JSONObject outValue = new JSONObject();
        outValue.put("status", 200);
        outValue.put("body", body);

        JSONObject row = new JSONObject();
        row.put("scope", "out");
        row.put("name", "status");
        row.put("op", "set");
        row.put("value", 200);

        JSONObject row2 = new JSONObject();
        row2.put("scope", "out");
        row2.put("name", "body");
        row2.put("op", "set");
        row2.put("value", body);

        JSONArray assignments = new JSONArray();
        assignments.add(row);
        assignments.add(row2);

        GraphNode node = assignNode(assignments);
        FlowRunContext ctx = new FlowRunContext();
        ctx.getIn().put("orderId", "ORD-1");

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        assertEquals(200, ((Number) ctx.getOut().get("status")).intValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> outBody = (Map<String, Object>) ctx.getOut().get("body");
        assertEquals("SUCCESS", outBody.get("code"));
        assertEquals("ORD-1", outBody.get("echo"));
        assertTrue(ctx.getFlow().isEmpty());
    }

    private static GraphNode assignNode(JSONArray assignments) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "计数");
        data.put("assignments", assignments);
        return GraphNode.builder().id("n_assign").type("assign").data(data).build();
    }

    private static JSONObject row(String name, String op, String value, Integer step, Integer ifMissing) {
        return row("flow", name, op, value, step, ifMissing);
    }

    private static JSONObject row(String scope, String name, String op, String value, Integer step, Integer ifMissing) {
        JSONObject row = new JSONObject();
        row.put("scope", scope);
        row.put("name", name);
        row.put("op", op);
        if (value != null) {
            row.put("value", value);
        }
        if (step != null) {
            row.put("step", step);
        }
        if (ifMissing != null) {
            row.put("ifMissing", ifMissing);
        }
        return row;
    }
}
