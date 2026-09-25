package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSON;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.PathNotFoundException;

/**
 * HTTP 响应 body 的 JsonPath 求值入口。
 * <p>
 * 支持下标、过滤器、通配、{@code length()} 等完整路径语法。
 * 入参 body 先序列化为 JSON 文本再解析，避免 Map/JSONObject 与解析器类型不兼容。
 * 路径缺叶时返回 {@code null}，不抛错。
 */
public final class JsonPathFacade {

    /** 缺叶 → null，不抛 PathNotFound */
    private static final Configuration CONFIG = Configuration.builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
            .build();

    private static final ParseContext PARSE = JsonPath.using(CONFIG);

    private JsonPathFacade() {
    }

    /**
     * 对 body 求 JsonPath。
     * <ul>
     *   <li>{@code expr} 必须以 {@code $} 开头，否则返回 null</li>
     *   <li>{@code $} 单独表示整个 body</li>
     *   <li>路径不存在、语法非法、非 JSON → null</li>
     * </ul>
     */
    public static Object eval(Object body, String expr) {
        if (body == null || expr == null) {
            return null;
        }
        String path = expr.trim();
        if (path.isEmpty() || path.charAt(0) != '$') {
            return null;
        }
        try {
            String json = toJsonString(body);
            if (json == null) {
                return null;
            }
            if ("$".equals(path)) {
                return normalizeResult(PARSE.parse(json).json());
            }
            return normalizeResult(PARSE.parse(json).read(path));
        } catch (PathNotFoundException e) {
            return null;
        } catch (RuntimeException e) {
            // 非法语法或无法解析的结构
            return null;
        }
    }

    /**
     * 将求值结果转为 Fastjson 可识别的 List/Map/标量，便于写入 flow 与步骤报告。
     */
    private static Object normalizeResult(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof String || result instanceof Number || result instanceof Boolean) {
            return result;
        }
        try {
            return JSON.parse(JSON.toJSONString(result));
        } catch (RuntimeException e) {
            return result;
        }
    }

    /**
     * 把相对路径补成绝对 JsonPath，供读 body 业务码等场景使用。
     * 已以 {@code $} 开头则原样返回（避免再拼成 {@code $.$.code}）；
     * 以 {@code [} 开头则前加 {@code $}；否则前加 {@code $.}。
     * 空串返回 {@code $}。
     */
    public static String toAbsolutePath(String relative) {
        if (relative == null || relative.isEmpty()) {
            return "$";
        }
        String r = relative.trim();
        if (r.startsWith("$")) {
            return r;
        }
        if (r.startsWith("[")) {
            return "$" + r;
        }
        return "$." + r;
    }

    /**
     * 检查 JsonPath 语法是否可用：必须以 {@code $} 开头、括号成对，且能编译并在空对象上试读。
     * 用于保存/确认前拦截坏路径；缺叶不算语法错误。
     */
    public static boolean isValidPath(String expr) {
        if (expr == null || expr.isBlank()) {
            return false;
        }
        String path = expr.trim();
        if (path.charAt(0) != '$') {
            return false;
        }
        if (!hasBalancedBrackets(path)) {
            return false;
        }
        try {
            JsonPath compiled = JsonPath.compile(path);
            PARSE.parse("{}").read(compiled);
            return true;
        } catch (PathNotFoundException e) {
            // 语法合法，只是空对象上没有该字段
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** {@code []} / {@code ()} 是否成对且无越界闭合。 */
    static boolean hasBalancedBrackets(String path) {
        int square = 0;
        int paren = 0;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '[') {
                square++;
            } else if (c == ']') {
                square--;
            } else if (c == '(') {
                paren++;
            } else if (c == ')') {
                paren--;
            }
            if (square < 0 || paren < 0) {
                return false;
            }
        }
        return square == 0 && paren == 0;
    }

    /**
     * 把任意 body 变成可 parse 的 JSON 文本。
     * 已是 JSON 字面量的字符串直接使用；其它对象走 Fastjson 序列化。
     */
    private static String toJsonString(Object body) {
        if (body instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            char c = trimmed.charAt(0);
            if (c == '{' || c == '[' || c == '"' || c == 't' || c == 'f' || c == 'n' || Character.isDigit(c) || c == '-') {
                return trimmed;
            }
            return JSON.toJSONString(s);
        }
        try {
            String json = JSON.toJSONString(body);
            if (json == null || json.isBlank() || "null".equals(json)) {
                return null;
            }
            return json;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
