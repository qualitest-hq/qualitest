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
 * 测 AssignNodeHandler：按 assignments 批量改 flow 变量并记录 assigns。
 * 边界：set/add；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AssignNodeHandlerTest
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
        assignments.add(row("count", "add", null, 2, 0));

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

    private static GraphNode assignNode(JSONArray assignments) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "计数");
        data.put("assignments", assignments);
        return GraphNode.builder().id("n_assign").type("assign").data(data).build();
    }

    private static JSONObject row(String name, String op, String value, Integer step, Integer ifMissing) {
        JSONObject row = new JSONObject();
        row.put("scope", "flow");
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
