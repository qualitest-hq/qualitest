package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI / 设计 patch 落图前，规范化 assign 节点 {@code data.assignments[]}。
 * <ul>
 *   <li>scope 固定为 flow</li>
 *   <li>op 空或非法 → set；合法：set/add/sub/mul/div</li>
 *   <li>name trim</li>
 * </ul>
 * 不补空 assignments（空列表由 GraphJsonValidator 硬拦）。
 */
public final class FlowDesignAssignNodeNormalizer {

    private static final Set<String> OPS = Set.of("set", "add", "sub", "mul", "div");

    private FlowDesignAssignNodeNormalizer() {
    }

    public static void normalize(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object raw = data.get("assignments");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        JSONArray next = new JSONArray();
        for (Object item : list) {
            JSONObject row = toObject(item);
            if (row == null) {
                continue;
            }
            row.put("scope", "flow");
            String name = row.getString("name");
            row.put("name", name != null ? name.trim() : "");
            row.put("op", normalizeOp(row.get("op")));
            next.add(row);
        }
        data.put("assignments", next);
    }

    /** 空/非法 op → set；合法取值原样小写归一。 */
    public static String normalizeOp(Object raw) {
        if (raw == null) {
            return "set";
        }
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || !OPS.contains(s)) {
            return "set";
        }
        return s;
    }

    private static JSONObject toObject(Object raw) {
        if (raw instanceof JSONObject obj) {
            return new JSONObject(obj);
        }
        if (raw instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        return null;
    }
}
