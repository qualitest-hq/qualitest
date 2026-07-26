package com.qualitest.flow.node.impl;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assert 节点执行器。
 * <p>
 * 对 {@code data.rules[]} 逐条调用 {@link com.qualitest.flow.context.CompareRuleEvaluator}，
 * 多条规则为 AND 关系；不负责 HTTP 状态码校验（由 HttpNodeHandler 在 HTTP 步完成）。
 */
@Component
public class AssertNodeHandler extends AbstractStubNodeHandler {

    public AssertNodeHandler() {
        super(FlowNodeType.ASSERT);
    }

    @Override
    public StepResult execute(FlowRunContext ctx, GraphNode node, String incomingEdgeId) {
        long t0 = System.currentTimeMillis();
        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
        String nodeName = resolveNodeName(node);

        JSONArray rules = toRulesArray(data.get("rules"));
        List<Map<String, Object>> ruleResults = new ArrayList<>();
        boolean allPassed = true;

        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                if (rule == null) {
                    continue;
                }
                boolean passed = CompareRuleEvaluator.eval(rule, ctx);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("left", rule.getString("left"));
                row.put("operator", rule.getOrDefault("operator", "eq"));
                row.put("right", rule.getString("right"));
                row.put("passed", passed);
                ruleResults.add(row);
                if (!passed) {
                    allPassed = false;
                }
            }
        }

        long durationMs = System.currentTimeMillis() - t0;
        Map<String, Object> assertDetails = Map.of("rules", ruleResults);

        if (!allPassed) {
            return StepResult.builder()
                    .nodeId(node.getId())
                    .nodeType(FlowNodeType.ASSERT.getCode())
                    .nodeName(nodeName)
                    .edgeId(incomingEdgeId)
                    .status(StepResult.STATUS_FAILED)
                    .durationMs(durationMs)
                    .assertDetails(assertDetails)
                    .flowAfter(copyFlow(ctx))
                    .error(StepError.of(FlowErrorCode.TF_ASSERT_FAILED, "断言未通过"))
                    .build();
        }

        return StepResult.builder()
                .nodeId(node.getId())
                .nodeType(FlowNodeType.ASSERT.getCode())
                .nodeName(nodeName)
                .edgeId(incomingEdgeId)
                .status(StepResult.STATUS_PASSED)
                .durationMs(durationMs)
                .assertDetails(assertDetails)
                .flowAfter(copyFlow(ctx))
                .build();
    }

    private static JSONArray toRulesArray(Object raw) {
        if (raw == null) {
            return new JSONArray();
        }
        if (raw instanceof JSONArray arr) {
            return arr;
        }
        if (raw instanceof List<?> list) {
            return JSONArray.parseArray(com.alibaba.fastjson2.JSON.toJSONString(list));
        }
        return JSONArray.parseArray(com.alibaba.fastjson2.JSON.toJSONString(raw));
    }
}
