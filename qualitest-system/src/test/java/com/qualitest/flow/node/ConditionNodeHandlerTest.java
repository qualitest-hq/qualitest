package com.qualitest.flow.node;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.impl.ConditionNodeHandler;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ConditionNodeHandler} 单元测试：验证「条件分支」节点的求值与路由决策。
 * <p>
 * 被测对象遍历节点 data 中的 {@code branches} 数组，用 {@link com.qualitest.flow.context.CompareRuleEvaluator}
 * 对 IF 分支的 conditions 求值；命中则记录 {@code branchTaken}（含 branchId 与 kind），
 * 供 {@link GraphWalker} 决定下一跳节点。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ConditionNodeHandlerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConditionNodeHandlerTest {

    private final ConditionNodeHandler handler = new ConditionNodeHandler();

    /**
     * {@code flow.code == 0} 满足 IF 分支条件 {@code flow.code eq 0}。
     * 期望：步骤 passed；{@code branchTaken.branchId == 'b_if'}，kind=if。
     */
    @Test
    @Order(1)
    void execute_hitsIfBranch() {
        begin("execute_hitsIfBranch");
        GraphNode node = conditionNode("b_if", "n_ok", "b_else", "n_fail");
        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("code", 0);

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("b_if", result.getBranchTaken().get("branchId"));
        assertEquals("if", result.getBranchTaken().get("kind"));
        log("branchTaken=" + result.getBranchTaken());
        end("execute_hitsIfBranch");
    }

    /**
     * {@code flow.code == 99} 不满足 IF 条件，应走 ELSE 分支。
     * 期望：步骤 passed；{@code branchTaken.branchId == 'b_else'}，kind=else。
     */
    @Test
    @Order(2)
    void execute_fallsThroughToElse() {
        begin("execute_fallsThroughToElse");
        GraphNode node = conditionNode("b_if", "n_ok", "b_else", "n_fail");
        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("code", 99);

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_PASSED, result.getStatus());
        assertEquals("b_else", result.getBranchTaken().get("branchId"));
        assertEquals("else", result.getBranchTaken().get("kind"));
        log("branchTaken=" + result.getBranchTaken());
        end("execute_fallsThroughToElse");
    }

    /**
     * IF 分支条件成立但 target 未配置（null）时无法确定下一跳。
     * 期望：步骤 failed，{@code result.error} 非空。
     */
    @Test
    @Order(3)
    void execute_failsWhenTargetMissing() {
        begin("execute_failsWhenTargetMissing");
        JSONObject ifBranch = branch("b_if", "if", null);
        ifBranch.put("conditions", conditions("flow.flag", "eq", "1"));
        JSONObject elseBranch = branch("b_else", "else", "n_fail");
        GraphNode node = nodeWithBranches(ifBranch, elseBranch);

        FlowRunContext ctx = new FlowRunContext();
        ctx.getFlow().put("flag", 1);

        StepResult result = handler.execute(ctx, node, null);
        assertEquals(StepResult.STATUS_FAILED, result.getStatus());
        assertNotNull(result.getError());
        log("error=" + result.getError().getCode());
        end("execute_failsWhenTargetMissing");
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
