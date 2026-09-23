package com.qualitest.flow.node.impl;


import com.qualitest.flow.run.RunStatus;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.CompareRuleEvaluator;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.validate.FlowNodeType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Condition 节点执行器。
 * <p>
 * 按 {@code data.branches[]} 顺序扫描（IF → ELIF → ELSE）：
 * <ul>
 *   <li>if/elif：{@code conditions[]} 全部成立则命中（AND）</li>
 *   <li>else：前序均未命中时作为兜底</li>
 * </ul>
 * 命中后本步标记 passed，并把命中分支写入 {@code branchTaken}（branchId / kind）。
 * 无有效 target 时后续不再走向下游，本流正常结束。
 */
@Component
public class ConditionNodeHandler extends AbstractStubNodeHandler {

    public ConditionNodeHandler() {
        super(FlowNodeType.CONDITION);
    }

    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        String nodeName = resolveNodeName(node);
        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();

        Object branchesObj = data.get("branches");
        if (!(branchesObj instanceof JSONArray branches) || branches.isEmpty()) {
            return failed(node, incomingEdgeId, nodeName, t0, ctx,
                    "condition 节点缺少 branches 配置");
        }

        JSONObject matched = null;
        for (int i = 0; i < branches.size(); i++) {
            JSONObject branch = branches.getJSONObject(i);
            if (branch == null) {
                continue;
            }
            String kind = branch.getString("kind");
            if ("else".equals(kind)) {
                matched = branch;
                break;
            }
            if (evalBranchConditions(branch, ctx)) {
                matched = branch;
                break;
            }
        }

        if (matched == null) {
            return failed(node, incomingEdgeId, nodeName, t0, ctx,
                    "未命中任何条件分支");
        }

        Map<String, Object> branchTaken = new LinkedHashMap<>();
        branchTaken.put("branchId", matched.getString("id"));
        branchTaken.put("kind", matched.getString("kind"));

        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.CONDITION.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(RunStatus.PASSED.getCode())
                .durationMs(Math.max(0, System.currentTimeMillis() - t0))
                .flowAfter(copyFlow(ctx))
                .branchTaken(branchTaken)
                .build();
    }

    /**
     * 求值单条分支：else 恒成立；if/elif 要求 conditions 全部成立。
     */
    static boolean evalBranchConditions(JSONObject branch, FlowRunContext ctx) {
        Object conditionsObj = branch.get("conditions");
        if (!(conditionsObj instanceof JSONArray conditions) || conditions.isEmpty()) {
            return false;
        }
        for (int i = 0; i < conditions.size(); i++) {
            JSONObject rule = conditions.getJSONObject(i);
            if (rule == null) {
                return false;
            }
            String left = rule.getString("left");
            if (left == null || left.isBlank()) {
                return false;
            }
            if (!CompareRuleEvaluator.eval(rule, ctx)) {
                return false;
            }
        }
        return true;
    }

    /** 构造失败步骤结果，错误码 TF_BRANCH_UNWIRED */
    private static StepResult failed(GraphNode node, String incomingEdgeId, String nodeName,
                                     long t0, FlowRunContext ctx, String message) {
        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.CONDITION.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(RunStatus.FAILED.getCode())
                .durationMs(Math.max(0, System.currentTimeMillis() - t0))
                .flowAfter(copyFlow(ctx))
                .error(StepError.of(FlowErrorCode.TF_BRANCH_UNWIRED, message))
                .build();
    }
}
