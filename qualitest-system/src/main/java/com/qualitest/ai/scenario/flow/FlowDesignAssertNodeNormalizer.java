package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 设计 patch 中 assert 节点 data 规范化。
 * <p>
 * 修正模型常见写法，使规则可被断言执行器识别：
 * <ul>
 *   <li>运算符：equals / == 等改为 eq，不等于类改为 ne</li>
 *   <li>左右值：去掉整段包裹的双花括号，例如 {{flow.mobile}} → flow.mobile</li>
 * </ul>
 */
public final class FlowDesignAssertNodeNormalizer {

    private FlowDesignAssertNodeNormalizer() {
    }

    /**
     * 就地规范化 assert 节点的 {@code rules} 数组。
     */
    public static void normalize(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object raw = data.get("rules");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        JSONArray normalized = new JSONArray();
        for (Object item : list) {
            JSONObject row = toRuleObject(item);
            if (row == null) {
                continue;
            }
            normalizeRule(row);
            normalized.add(row);
        }
        if (!normalized.isEmpty()) {
            data.put("rules", normalized);
        }
    }

    /**
     * 规范化单条断言规则的 left / operator / right。
     */
    static void normalizeRule(JSONObject rule) {
        if (rule == null) {
            return;
        }
        if (rule.containsKey("left")) {
            rule.put("left", stripMustache(rule.get("left")));
        }
        if (rule.containsKey("right")) {
            rule.put("right", stripMustache(rule.get("right")));
        }
        if (rule.containsKey("operator")) {
            rule.put("operator", normalizeOperator(rule.get("operator")));
        }
    }

    /**
     * 将常见别名统一为引擎支持的运算符（eq / ne 等）；未知值转小写后原样返回。
     */
    static String normalizeOperator(Object raw) {
        if (raw == null) {
            return "eq";
        }
        String op = String.valueOf(raw).trim();
        if (op.isEmpty()) {
            return "eq";
        }
        String lower = op.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "equals", "equal", "==" -> "eq";
            case "notequals", "not_equals", "neq", "!=" -> "ne";
            default -> lower;
        };
    }

    /**
     * 若字符串被双花括号完整包裹，则去掉外壳并 trim；非字符串原样返回。
     * 例如 {{flow.mobile}} → flow.mobile。
     */
    static Object stripMustache(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String text)) {
            return raw;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{{") && trimmed.endsWith("}}") && trimmed.length() >= 4) {
            return trimmed.substring(2, trimmed.length() - 2).trim();
        }
        return text;
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
