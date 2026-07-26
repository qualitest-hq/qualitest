package com.qualitest.flow.node;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.AssignNodeHandler;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AssignNodeHandler} 单元测试：验证「赋值」节点对 flow 变量的批量修改。
 * <p>
 * 被测对象读取节点 data 中的 {@code assignments} 数组，按 op（set / add 等）依次修改
 * {@code flow} 作用域变量，并在 {@link StepResult#getAssigns()} 中记录每条赋值的前后快照。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=AssignNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssignNodeHandlerTest {

    private final AssignNodeHandler handler = new AssignNodeHandler();

    /**
     * 连续两条赋值：先 set {@code pollAttempt=0}，再 add step=1。
     * 期望：步骤 passed；{@code flow.pollAttempt == 1}；
     * assigns 列表 2 条，分别记录 after=0 和 after=1。
     */
    @Test
    @Order(1)
    void execute_setAndAdd() {
        begin("execute_setAndAdd");
        JSONArray assignments = new JSONArray();
        assignments.add(row("pollAttempt", "set", "0", null, null));
        assignments.add(row("pollAttempt", "add", null, 1, 0));

        GraphNode node = assignNode(assignments);
        FlowRunContext ctx = new FlowRunContext();

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals(1L, ctx.getFlow().get("pollAttempt"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assigns = (List<Map<String, Object>>) result.getAssigns();
        assertEquals(2, assigns.size());
        assertEquals(0L, assigns.get(0).get("after"));
        assertEquals(1L, assigns.get(1).get("after"));
        log("flow.pollAttempt=" + ctx.getFlow().get("pollAttempt"));
        end("execute_setAndAdd");
    }

    /**
     * op 为 add 时在现有值基础上累加 step。
     * 初始 {@code flow.count=3}，add step=2。
     * 期望：{@code flow.count == 5}；assigns 中 op 为 add。
     */
    @Test
    @Order(2)
    void execute_addWithStep() {
        begin("execute_addWithStep");
        JSONArray assignments = new JSONArray();
        assignments.add(row("count", "add", null, 2, 0));

        GraphNode node = assignNode(assignments);
        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("count", 3);

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals(5L, ctx.getFlow().get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assigns = (List<Map<String, Object>>) result.getAssigns();
        assertEquals(1, assigns.size());
        assertEquals("add", assigns.get(0).get("op"));
        assertEquals(5L, assigns.get(0).get("after"));
        log("count after=" + ctx.getFlow().get("count"));
        end("execute_addWithStep");
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
