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

    /**
     * 前提：flow.token=t-1，assignment 写对象字面量 {"body":{"token":"{{flow.token}}"}}。
     * 期望：嵌套占位符被解析，flow.out.body.token=t-1（不是字面量）。
     */
    @Test
    @Order(3)
    @DisplayName("执行：对象字面量里的占位符逐层解析")
    void execute_resolvesPlaceholdersInObjectLiteral() {
        JSONObject row = new JSONObject();
        row.put("scope", "flow");
        row.put("name", "out");
        row.put("op", "set");
        JSONObject body = new JSONObject();
        body.put("token", "{{flow.token}}");
        JSONObject value = new JSONObject();
        value.put("body", body);
        row.put("value", value);
        JSONArray assignments = new JSONArray();
        assignments.add(row);

        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("token", "t-1");

        StepResult result = handler.execute(ctx, assignNode(assignments), null);

        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) ctx.getFlow().get("out");
        @SuppressWarnings("unchecked")
        Map<String, Object> outBody = (Map<String, Object>) out.get("body");
        assertEquals("t-1", outBody.get("token"));
    }

    /**
     * 前提：flow.tag=a，assignment 写数组字面量 ["{{flow.tag}}", "x-{{flow.tag}}"]。
     * 期望：数组元素里的占位符都完成替换。
     */
    @Test
    @Order(4)
    @DisplayName("执行：数组字面量里的占位符逐层解析")
    void execute_resolvesPlaceholdersInArrayLiteral() {
        JSONObject row = new JSONObject();
        row.put("scope", "flow");
        row.put("name", "list");
        row.put("op", "set");
        JSONArray value = new JSONArray();
        value.add("{{flow.tag}}");
        value.add("x-{{flow.tag}}");
        row.put("value", value);
        JSONArray assignments = new JSONArray();
        assignments.add(row);

        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("tag", "a");

        StepResult result = handler.execute(ctx, assignNode(assignments), null);

        assertEquals(RunStatus.PASSED.getCode(), result.getStatus());
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) ctx.getFlow().get("list");
        assertEquals("a", list.get(0));
        assertEquals("x-a", list.get(1));
    }

    private static GraphNode assignNode(JSONArray assignments) {
        Map<String, Object> data = new HashMap<>();
        data.put("assignments", assignments);
        return GraphNode.builder().id("n_assign").type("assign").name("计数").data(data).build();
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
