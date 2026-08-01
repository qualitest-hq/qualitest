package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.CompareRuleEvaluator;
import com.qualitest.flow.context.PlaceholderResolver;

import java.util.List;
import java.util.Map;

/**
 * AI / 设计 patch 落图前，对 assert、condition 比较规则做就地改写，避免常见坏写法进入确认与运行。
 * <ul>
 *   <li>运算符别名归一（equals → eq，notempty → exists 等）</li>
 *   <li>去掉整段包裹的 {@code {{…}}}</li>
 *   <li>纯 {@code $…} 左值改成 {@code http.body…}</li>
 * </ul>
 */
public final class FlowDesignAssertNodeNormalizer {

    private FlowDesignAssertNodeNormalizer() {
    }

    /** 规范化 assert 节点 {@code data.rules[]}。 */
    public static void normalize(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object raw = data.get("rules");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        data.put("rules", normalizeRuleList(list));
    }

    /** 规范化 condition 节点各分支 {@code branches[].conditions[]}。 */
    public static void normalizeConditionBranches(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object branchesRaw = data.get("branches");
        if (!(branchesRaw instanceof List<?> branches) || branches.isEmpty()) {
            return;
        }
        for (Object branchItem : branches) {
            if (!(branchItem instanceof Map<?, ?>)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> branch = (Map<String, Object>) branchItem;
            Object conditionsRaw = branch.get("conditions");
            if (!(conditionsRaw instanceof List<?> conditions) || conditions.isEmpty()) {
                continue;
            }
            branch.put("conditions", normalizeRuleList(conditions));
        }
    }

    /** 逐条规范化规则列表。 */
    private static JSONArray normalizeRuleList(List<?> list) {
        JSONArray normalized = new JSONArray();
        for (Object item : list) {
            JSONObject row = toRuleObject(item);
            if (row == null) {
                continue;
            }
            normalizeRule(row);
            normalized.add(row);
        }
        return normalized;
    }

    /** 规范化单条规则的 left / operator / right。 */
    static void normalizeRule(JSONObject rule) {
        if (rule == null) {
            return;
        }
        if (rule.containsKey("left")) {
            Object left = CompareRuleEvaluator.stripMustache(rule.get("left"));
            if (left instanceof String s) {
                left = PlaceholderResolver.normalizeAssertLeftPath(s);
            }
            rule.put("left", left);
        }
        if (rule.containsKey("right")) {
            rule.put("right", CompareRuleEvaluator.stripMustache(rule.get("right")));
        }
        if (rule.containsKey("operator")) {
            rule.put("operator", normalizeOperator(rule.get("operator")));
        }
    }

    /** 运算符别名归一。 */
    static String normalizeOperator(Object raw) {
        if (raw == null) {
            return "eq";
        }
        return CompareRuleEvaluator.normalizeOperator(String.valueOf(raw));
    }

    /** 去掉整段 {@code {{…}}} 外壳。 */
    static Object stripMustache(Object raw) {
        return CompareRuleEvaluator.stripMustache(raw);
    }

    private static JSONObject toRuleObject(Object raw) {
        if (raw instanceof JSONObject obj) {
            return new JSONObject(obj);
        }
        if (raw instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        return null;
    }
}
