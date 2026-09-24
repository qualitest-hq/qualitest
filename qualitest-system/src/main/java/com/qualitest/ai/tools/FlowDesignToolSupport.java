package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Map;

/**
 * Flow Design 工具公共辅助：参数解析、错误响应与列表封装。
 * <p>
 * 工具失败时返回 JSON 顶层含 {@code error} 字段。
 * 列表类结果组装后会按字节上限裁剪 items。
 */
public final class FlowDesignToolSupport {

    private FlowDesignToolSupport() {
    }

    public static String errorJson(String message) {
        return JSON.toJSONString(Map.of("error", message));
    }

    /**
     * 工具返回 JSON 是否表示业务失败（顶层含 {@code error} 字段）。
     */
    public static boolean isErrorResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return false;
        }
        try {
            JSONObject obj = JSON.parseObject(resultJson);
            return obj != null && obj.containsKey("error");
        } catch (Exception ex) {
            return false;
        }
    }

    public static String stringArg(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 解析长整型参数。
     * 支持 Number 或可解析的数字字符串；空串、非法格式返回 null。
     */
    public static Long longArg(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析整型参数。
     * 支持 Number 或可解析的数字字符串；空串、非法格式返回 null。
     */
    public static Integer intArg(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int resolveSearchLimit(Object limitArg, int maxSearchApis) {
        return resolveBoundedLimit(limitArg, maxSearchApis, maxSearchApis);
    }

    /** 列举类工具（list_flows、list_subflow_templates）的条数上限解析。 */
    public static int resolveListLimit(Object limitArg, int defaultLimit, int maxListFlows) {
        return resolveBoundedLimit(limitArg, defaultLimit, maxListFlows);
    }

    private static int resolveBoundedLimit(Object limitArg, int defaultLimit, int maxCap) {
        int limit = defaultLimit;
        if (limitArg instanceof Number n) {
            limit = n.intValue();
        } else if (limitArg != null) {
            String s = String.valueOf(limitArg).trim();
            if (!s.isEmpty()) {
                try {
                    limit = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }
        }
        if (limit < 1) {
            limit = 1;
        }
        return Math.min(limit, maxCap);
    }

    public static String missingGraphJsonError() {
        JSONObject result = new JSONObject();
        result.put("error", "画布为空");
        result.put("hint", "请在 arguments 或信封中传入 testFlowId（系统会自动加载 graphJson），"
                + "或先调用 list_flows 选定测试流后再调用 get_flow 获取 graphJson");
        return result.toJSONString();
    }

    /**
     * 组装 items 列表结果（含条数截断标记），再按字节上限裁剪 items。
     *
     * @param items     业务条目
     * @param truncated 是否因条数上限已截断
     * @param hint      条数截断说明，可空
     * @param maxBytes  返回 JSON 字节上限
     */
    public static String buildItemsResult(JSONArray items, boolean truncated, String hint, int maxBytes) {
        JSONObject result = new JSONObject();
        result.put("items", items);
        result.put("truncated", truncated);
        if (truncated && hint != null && !hint.isBlank()) {
            result.put("hint", hint);
        }
        return ToolResultByteFit.fitItemsList(result, maxBytes);
    }

    /**
     * 将数据库 graph_json 列（字符串）解析为 JSON 对象供工具返回。
     */
    public static Object parseGraphJsonField(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSON.parse(graphJson.trim());
        } catch (Exception e) {
            return graphJson;
        }
    }

    public static Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            JSONObject obj = JSON.parseObject(argumentsJson);
            return obj != null ? obj : Map.of();
        } catch (Exception e) {
            throw new IllegalArgumentException("arguments 非合法 JSON");
        }
    }
}
