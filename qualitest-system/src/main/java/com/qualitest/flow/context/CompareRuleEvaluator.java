package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSONObject;

/**
 * 断言与条件分支的比较规则求值。
 * <p>
 * 左值经 {@link PlaceholderResolver#resolvePathSegment} 从运行时上下文读取；
 * 右值经 lenient 占位符替换后再比较。
 */
public final class CompareRuleEvaluator {

    private static final PlaceholderResolver LENIENT = PlaceholderResolver.lenient();

    private CompareRuleEvaluator() {
    }

    /**
     * 对单条规则求值。
     *
     * @param rule 含 {@code left}、{@code operator}、{@code right} 的 JSON 对象
     * @param ctx  当前 Run 上下文
     * @return 规则是否成立；未知运算符返回 false
     */
    public static boolean eval(JSONObject rule, FlowRunContext ctx) {
        if (rule == null) {
            return false;
        }
        String leftKey = String.valueOf(rule.getOrDefault("left", "")).trim();
        Object leftRaw = LENIENT.resolvePathSegment(ctx, leftKey);
        String op = rule.getString("operator");
        if (op == null || op.isEmpty()) {
            op = "eq";
        }

        if ("exists".equals(op)) {
            return leftRaw != null && !"".equals(String.valueOf(leftRaw));
        }

        Object left = coerceComparable(leftRaw);
        Object right = coerceComparable(LENIENT.resolve(
                rule.getString("right") != null ? rule.getString("right") : "",
                ctx
        ));

        return switch (op) {
            case "eq" -> compareEquals(left, right);
            case "ne" -> !compareEquals(left, right);
            case "gt" -> toDouble(left) > toDouble(right);
            case "lt" -> toDouble(left) < toDouble(right);
            case "gte" -> toDouble(left) >= toDouble(right);
            case "lte" -> toDouble(left) <= toDouble(right);
            case "contains" -> String.valueOf(left).contains(String.valueOf(right));
            case "not_contains" -> !String.valueOf(left).contains(String.valueOf(right));
            default -> false;
        };
    }

    private static boolean compareEquals(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right) || String.valueOf(left).equals(String.valueOf(right));
    }

    /**
     * 将值规范为可比较形态：整型/浮点字面量解析为数值，其余为字符串。
     */
    static Object coerceComparable(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return val;
        }
        String s = String.valueOf(val).trim();
        if (s.isEmpty()) {
            return "";
        }
        try {
            if (!s.contains(".")) {
                long n = Long.parseLong(s);
                return n;
            }
            double d = Double.parseDouble(s);
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return (long) d;
            }
            return d;
        } catch (NumberFormatException ignored) {
            return s;
        }
    }

    private static double toDouble(Object val) {
        if (val == null) {
            return 0;
        }
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
