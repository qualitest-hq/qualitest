package com.qualitest.flow.node;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.ConditionNodeHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 ConditionNodeHandler：按 branches 条件求值并写入 branchTaken。
 * 边界：命中 if / 落 else / target 缺失失败；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ConditionNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConditionNodeHandlerTest {

    private final ConditionNodeHandler handler = new ConditionNodeHandler();

    /**
     * 前提：flow.code=0，IF 条件 eq 0。
     * 期望：passed；branchTaken.branchId=b_if，kind=if。
     */
    @Test
    @Order(1)
    @DisplayName("执行：条件命中 IF 分支")
    void execute_hitsIfBranch() {
        GraphNode node = conditionNode("b_if", "n_ok", "b_else", "n_fail");
        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("code", 0);

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("b_if", result.getBranchTaken().get("branchId"));
        assertEquals("if", result.getBranchTaken().get("kind"));
    }

    /**
     * 前提：flow.code=99，不满足 IF。
     * 期望：passed；branchTaken.branchId=b_else，kind=else。
     */
    @Test
    @Order(2)
    @DisplayName("执行：条件不满足时落入 ELSE")
    void execute_fallsThroughToElse() {
        GraphNode node = conditionNode("b_if", "n_ok", "b_else", "n_fail");
        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("code", 99);

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("b_else", result.getBranchTaken().get("branchId"));
        assertEquals("else", result.getBranchTaken().get("kind"));
    }

    /**
     * 前提：IF 条件成立但 target 为 null。
     * 期望：failed；result.error 非空。
     */
    @Test
    @Order(3)
    @DisplayName("执行：命中分支缺 target 时失败")
    void execute_failsWhenTargetMissing() {
        JSONObject ifBranch = branch("b_if", "if", null);
        ifBranch.put("conditions", conditions("flow.flag", "eq", "1"));
        JSONObject elseBranch = branch("b_else", "else", "n_fail");
        GraphNode node = nodeWithBranches(ifBranch, elseBranch);

        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("flag", 1);

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertNotNull(result.getError());
    }

    /**
     * 前提：IF 条件成立且 terminal=true，无 target。
     * 期望：passed；branchTaken 含 terminal。
     */
    @Test
    @Order(4)
    @DisplayName("执行：terminal IF 分支无 target 时通过")
    void execute_passesTerminalBranchWithoutTarget() {
        JSONObject ifBranch = branch("b_if", "if", null);
        ifBranch.put("terminal", true);
        ifBranch.put("conditions", conditions("flow.flag", "eq", "1"));
        JSONObject elseBranch = branch("b_else", "else", "n_fail");
        GraphNode node = nodeWithBranches(ifBranch, elseBranch);

        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("flag", 1);

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("b_if", result.getBranchTaken().get("branchId"));
        assertEquals(Boolean.TRUE, result.getBranchTaken().get("terminal"));
    }

    /** 构造含 IF/ELSE 两分支的 condition 节点 */
    private static GraphNode conditionNode(String ifId, String ifTarget, String elseId, String elseTarget) {
        JSONObject ifBranch = branch(ifId, "if", ifTarget);
        ifBranch.put("conditions", conditions("flow.code", "eq", "0"));
        JSONObject elseBranch = branch(elseId, "else", elseTarget);
        return nodeWithBranches(ifBranch, elseBranch);
    }

    private static GraphNode nodeWithBranches(JSONObject ifBranch, JSONObject elseBranch) {
        JSONArray branches = new JSONArray();
        branches.add(ifBranch);
        branches.add(elseBranch);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "测试分支");
        data.put("branches", branches);
        return GraphNode.builder().id("n_cond").type("condition").data(data).build();
    }

    private static JSONObject branch(String id, String kind, String target) {
        JSONObject b = new JSONObject();
        b.put("id", id);
        b.put("kind", kind);
        if (target != null) {
            b.put("target", target);
        }
        return b;
    }

    private static JSONArray conditions(String left, String op, String right) {
        JSONArray arr = new JSONArray();
        JSONObject rule = new JSONObject();
        rule.put("left", left);
        rule.put("operator", op);
        rule.put("right", right);
        arr.add(rule);
        return arr;
    }
}
