package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSONObject;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * 断言节点、条件分支的单条比较规则求值。
 * <p>
 * 左值从运行时上下文按路径取值；右值先做宽松占位符替换再比较。
 * 运算符别名、集合语义在此统一处理。
 */
public final class CompareRuleEvaluator {

    private static final PlaceholderResolver LENIENT = PlaceholderResolver.lenient();

    private CompareRuleEvaluator() {
    }

    /**
     * 求值是否通过。未知运算符返回 false。
     *
     * @param rule 含 left / operator / right
     * @param ctx  当前 Run 上下文
     */
    public static boolean eval(JSONObject rule, FlowRunContext ctx) {
        return evalDetailed(rule, ctx).passed();
    }

    /**
     * 求值并带回左值实际解析结果，供步骤报告展示。
     */
    public static EvalDetail evalDetailed(JSONObject rule, FlowRunContext ctx) {
        if (rule == null) {
            return EvalDetail.failed(null);
        }
        String leftKey = stripMustache(String.valueOf(rule.getOrDefault("left", "")).trim());
        Object leftRaw = LENIENT.resolvePathSegment(ctx, leftKey);
        String op = normalizeOperator(rule.getString("operator"));

        if ("exists".equals(op)) {
            return new EvalDetail(existsValue(leftRaw), leftRaw);
        }

        Object leftUnboxed = unboxSingleton(leftRaw);
        // 多元素时标量比较直接失败（只有恰好 1 个元素才拆箱）
        if (isMultiValue(leftRaw) && isScalarCompareOp(op)) {
            return new EvalDetail(false, leftRaw);
        }

        Object left = coerceComparable(leftUnboxed);
        Object right = coerceComparable(resolveRightValue(rule.getString("right"), ctx));

        boolean passed = switch (op) {
            case "eq" -> compareEquals(left, right);
            case "ne" -> !compareEquals(left, right);
            case "gt" -> toDouble(left) > toDouble(right);
            case "lt" -> toDouble(left) < toDouble(right);
            case "gte" -> toDouble(left) >= toDouble(right);
            case "lte" -> toDouble(left) <= toDouble(right);
            case "contains" -> containsValue(leftRaw, left, right);
            case "not_contains" -> !containsValue(leftRaw, left, right);
            default -> false;
        };
        return new EvalDetail(passed, leftRaw);
    }

    /** 是否通过 + 左值实测。 */
    public record EvalDetail(boolean passed, Object leftActual) {
        static EvalDetail failed(Object leftActual) {
            return new EvalDetail(false, leftActual);
        }
    }

    /**
     * 运算符别名归一：{@code equals}/{@code ==} → eq；不等别名 → ne；
     * {@code notempty}/{@code not_empty}/{@code isNotEmpty} → exists；空 → eq。
     */
    public static String normalizeOperator(String raw) {
        if (raw == null || raw.isBlank()) {
            return "eq";
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "equals", "equal", "==" -> "eq";
            case "notequals", "not_equals", "neq", "!=" -> "ne";
            case "notempty", "not_empty", "isnotempty", "is_not_empty" -> "exists";
            case "lessthan", "less_than", "<" -> "lt";
            case "lessthanorequal", "less_than_or_equal", "ltequal", "<=" -> "lte";
            case "greaterthan", "greater_than", ">" -> "gt";
            case "greaterthanorequal", "greater_than_or_equal", "gtequal", ">=" -> "gte";
            default -> lower;
        };
    }

    /**
     * 若整段被 {@code {{…}}} 包裹则去掉外壳；非字符串原样返回。
     */
    public static Object stripMustache(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String text)) {
            return raw;
        }
        return stripMustache(text);
    }

    /** 字符串版：整段 {@code {{flow.x}}} → {@code flow.x}。 */
    public static String stripMustache(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{{") && trimmed.endsWith("}}") && trimmed.length() >= 4) {
            return trimmed.substring(2, trimmed.length() - 2).trim();
        }
        return trimmed;
    }

    /**
     * exists：非 null；非空串；Collection/Map/数组非空则通过，标量（含 0、false）视为存在。
     */
    static boolean existsValue(Object leftRaw) {
        if (leftRaw == null) {
            return false;
        }
        if (leftRaw instanceof String s) {
            return !s.isEmpty();
        }
        if (leftRaw instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (leftRaw instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        if (leftRaw.getClass().isArray()) {
            return Array.getLength(leftRaw) > 0;
        }
        return true;
    }

    /**
     * contains：左值为列表/数组时，任一元素转字符串包含右值即通过；否则按标量字符串包含判断。
     */
    private static boolean containsValue(Object leftRaw, Object leftCoerced, Object right) {
        String rightStr = String.valueOf(right);
        if (leftRaw instanceof Collection<?> c) {
            for (Object item : c) {
                if (String.valueOf(item).contains(rightStr)) {
                    return true;
                }
            }
            return false;
        }
        if (leftRaw != null && leftRaw.getClass().isArray()) {
            int n = Array.getLength(leftRaw);
            for (int i = 0; i < n; i++) {
                if (String.valueOf(Array.get(leftRaw, i)).contains(rightStr)) {
                    return true;
                }
            }
            return false;
        }
        return String.valueOf(leftCoerced).contains(rightStr);
    }

    /** eq/ne/gt/lt/gte/lte：需要标量左值的比较运算符。 */
    private static boolean isScalarCompareOp(String op) {
        return "eq".equals(op) || "ne".equals(op)
                || "gt".equals(op) || "lt".equals(op)
                || "gte".equals(op) || "lte".equals(op);
    }

    /** 集合/数组恰好 1 个元素时取出该元素，否则原样返回。 */
    static Object unboxSingleton(Object leftRaw) {
        if (leftRaw instanceof Collection<?> c) {
            if (c.size() == 1) {
                return c.iterator().next();
            }
            return leftRaw;
        }
        if (leftRaw != null && leftRaw.getClass().isArray()) {
            if (Array.getLength(leftRaw) == 1) {
                return Array.get(leftRaw, 0);
            }
        }
        return leftRaw;
    }

    /** 集合/数组元素个数 &gt; 1。 */
    static boolean isMultiValue(Object leftRaw) {
        if (leftRaw instanceof Collection<?> c) {
            return c.size() > 1;
        }
        if (leftRaw != null && leftRaw.getClass().isArray()) {
            return Array.getLength(leftRaw) > 1;
        }
        return false;
    }

    /**
     * 右值解析：支持 {@code {{flow.x}}} 占位符，也支持裸写 {@code flow.x} / {@code env.x} /
     * {@code asset.x} / {@code http…} / {@code $…}（与左值路径同语义）。
     */
    static Object resolveRightValue(String rightRaw, FlowRunContext ctx) {
        if (rightRaw == null) {
            return "";
        }
        String trimmed = rightRaw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        // 整段为作用域路径时按左值同款解析，避免 flow.balanceBefore 被当成字面量
        String asPath = stripMustache(trimmed);
        if (looksLikeScopePath(asPath)) {
            Object pathValue = LENIENT.resolvePathSegment(ctx, asPath);
            if (pathValue != null) {
                return pathValue;
            }
        }
        return LENIENT.resolve(trimmed, ctx);
    }

    /** 是否像可解析的作用域路径（裸写右值场景）。 */
    static boolean looksLikeScopePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String p = path.trim();
        return p.startsWith("flow.")
                || p.startsWith("env.")
                || p.startsWith("asset.")
                || p.startsWith("http.")
                || p.startsWith("$");
    }

    private static boolean compareEquals(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        // JSON 金额常为 Double(195.0)，断言右值常为整型/整数字符串 → 按数值比较
        if (left instanceof Number || right instanceof Number) {
            return Double.compare(toDouble(left), toDouble(right)) == 0;
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    /**
     * 规范为可比较值：Number 保留；纯数字字符串解析为数值；集合/数组保持原样；其余为字符串。
     */
    static Object coerceComparable(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return val;
        }
        if (val instanceof Collection<?> || (val.getClass().isArray())) {
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
