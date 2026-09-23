package com.qualitest.project.report;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 失败摘要上的关联流程变量。
 * <p>
 * 从单步 step_details 取出与本步相关的 flow 键值，格式化为短文案，
 * 供 HTML 报告失败摘要挂载；非整份 flow 快照。
 */
final class RelatedFlowVars {

    /** 单个变量值展示的最大字符数，超出截断并加省略号 */
    private static final int VALUE_MAX = 80;

    private RelatedFlowVars() {}

    /**
     * 收集本步关联变量：键 → 短展示值。
     * 来源包括 extracts（scope 为 flow 或缺省）、assigns、script.writes、
     * 以及断言规则 left 上的 flow. 路径；断言若带 leftActual 则优先用实测值，
     * 否则从 flowAfter 取值。不读取 right，不回退整份快照。
     *
     * @param details 步骤详情 JSON；null 返回空 Map
     * @return 有序 Map；无相关项时为空 Map
     */
    static Map<String, String> pick(JSONObject details) {
        Map<String, String> empty = Map.of();
        if (details == null) {
            return empty;
        }
        JSONObject flowAfter = details.getJSONObject("flowAfter");
        Map<String, String> out = new LinkedHashMap<>();

        collectExtracts(details.getJSONArray("extracts"), flowAfter, out);
        JSONObject http = details.getJSONObject("http");
        if (http != null) {
            collectExtracts(http.getJSONArray("extracts"), flowAfter, out);
        }
        collectNamed(details.getJSONArray("assigns"), "name", flowAfter, out);
        JSONObject script = details.getJSONObject("script");
        if (script != null) {
            collectNamed(script.getJSONArray("writes"), "key", flowAfter, out);
        }
        JSONObject assertObj = details.getJSONObject("assert");
        if (assertObj != null) {
            collectAssert(assertObj.getJSONArray("rules"), flowAfter, out);
        }
        return out.isEmpty() ? empty : out;
    }

    /**
     * 收集提取变量名：仅 scope 为空、缺省或为 flow 的项；值取自 flowAfter。
     *
     * @param extracts  提取数组
     * @param flowAfter 本步结束后的 flow 快照
     * @param out       输出 Map（已有键不覆盖）
     */
    private static void collectExtracts(JSONArray extracts, JSONObject flowAfter, Map<String, String> out) {
        if (extracts == null || extracts.isEmpty()) {
            return;
        }
        for (int i = 0; i < extracts.size(); i++) {
            JSONObject e = extracts.getJSONObject(i);
            if (e == null) {
                continue;
            }
            String name = e.getString("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            String scope = e.getString("scope");
            if (scope == null || scope.isBlank() || "flow".equalsIgnoreCase(scope.trim())) {
                putFromFlowAfter(name.trim(), flowAfter, out);
            }
        }
    }

    /**
     * 从对象数组按指定字段收集键名，再从 flowAfter 取值写入 out。
     *
     * @param rows      如 assigns / writes
     * @param field     键名所在字段（如 name、key）
     * @param flowAfter 本步 flow 快照
     * @param out       输出 Map
     */
    private static void collectNamed(
            JSONArray rows, String field, JSONObject flowAfter, Map<String, String> out) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (row == null) {
                continue;
            }
            String key = row.getString(field);
            if (key != null && !key.isBlank()) {
                putFromFlowAfter(key.trim(), flowAfter, out);
            }
        }
    }

    /**
     * 从断言规则收集关联变量：left 须为 flow. 路径；
     * 规则含 leftActual 时用实测值，否则用 flowAfter 中对应键。
     *
     * @param rules     断言规则数组
     * @param flowAfter 本步 flow 快照
     * @param out       输出 Map
     */
    private static void collectAssert(JSONArray rules, JSONObject flowAfter, Map<String, String> out) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            JSONObject r = rules.getJSONObject(i);
            if (r == null) {
                continue;
            }
            String key = flowKeyFromPath(r.getString("left"));
            if (key == null) {
                continue;
            }
            if (r.containsKey("leftActual")) {
                out.put(key, formatValue(r.get("leftActual")));
            } else {
                putFromFlowAfter(key, flowAfter, out);
            }
        }
    }

    /**
     * 从路径文案解析 flow 变量键：仅接受以 {@code flow.} 开头的写法，返回点号后整段；
     * 非该前缀或前缀后为空则返回 null。
     *
     * @param path 如 flow.amount
     * @return 变量键，或 null
     */
    static String flowKeyFromPath(String path) {
        if (path == null) {
            return null;
        }
        String p = path.trim();
        if (!p.startsWith("flow.")) {
            return null;
        }
        String key = p.substring("flow.".length());
        return key.isEmpty() ? null : key;
    }

    /**
     * 若 flowAfter 含该键且 out 尚未写入该键，则写入格式化后的值。
     *
     * @param key       变量名
     * @param flowAfter 本步 flow 快照
     * @param out       输出 Map
     */
    private static void putFromFlowAfter(String key, JSONObject flowAfter, Map<String, String> out) {
        if (key == null || key.isBlank() || out.containsKey(key)) {
            return;
        }
        if (flowAfter == null || !flowAfter.containsKey(key)) {
            return;
        }
        out.put(key, formatValue(flowAfter.get(key)));
    }

    /**
     * 将变量值格式化为短展示串：null → {@code null}；标量直接转字符串；
     * 对象 / 数组转 JSON 后截断。
     *
     * @param value 原值
     * @return 展示文案
     */
    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return shorten(String.valueOf(value), VALUE_MAX);
        }
        try {
            return shorten(JSON.toJSONString(value), VALUE_MAX);
        } catch (Exception e) {
            return shorten(String.valueOf(value), VALUE_MAX);
        }
    }

    /**
     * 超长截断并在末尾加省略号。
     *
     * @param s   原文
     * @param max 最大保留长度（不含省略号时的阈值）
     * @return 截断结果
     */
    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}
