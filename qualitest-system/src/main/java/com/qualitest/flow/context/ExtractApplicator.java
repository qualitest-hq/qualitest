package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 响应提取：按 http 节点 {@code data.extracts[]} 配置从响应取值并写入运行时上下文。
 */
public final class ExtractApplicator {

    private ExtractApplicator() {
    }

    /**
     * 执行提取列表。
     *
     * @param extracts http 节点配置的 extracts 数组
     * @param ctx      当前 Run 上下文（就地修改 flow / env / asset）
     * @param response 本步 HTTP 响应快照
     * @return 已应用项，每项含 name、scope、value，供步骤报告使用
     */
    public static List<JSONObject> apply(JSONArray extracts, FlowRunContext ctx, FlowRunContext.HttpResponseSnapshot response) {
        List<JSONObject> applied = new ArrayList<>();
        if (extracts == null || response == null) {
            return applied;
        }
        for (int i = 0; i < extracts.size(); i++) {
            JSONObject ex = extracts.getJSONObject(i);
            if (ex == null) {
                continue;
            }
            Object val = resolveExtractValue(ex, response);
            String scope = ex.getString("scope");
            if (scope == null || scope.isEmpty()) {
                scope = "flow";
            }
            String name = ex.getString("name");
            if ((name == null || name.isEmpty()) && "flow".equals(scope)) {
                continue;
            }

            writeExtractValue(ctx, ex, scope, name, val);

            JSONObject row = new JSONObject();
            String label = name != null && !name.isEmpty() ? name : ex.getString("entryKey");
            row.put("name", label != null ? label : "");
            row.put("scope", scope);
            row.put("value", val);
            applied.add(row);
        }
        return applied;
    }

    /**
     * 按 from 字段从响应解析原始值；regex 暂未实现，返回 null。
     */
    private static Object resolveExtractValue(JSONObject ex, FlowRunContext.HttpResponseSnapshot response) {
        String from = ex.getString("from");
        if (from == null) {
            from = "body";
        }
        return switch (from) {
            case "body" -> PlaceholderResolver.simpleJsonPath(response.getBody(), ex.getString("expr"));
            case "header" -> resolveHeaderValue(response.getHeaders(), ex.getString("expr"));
            case "status" -> response.getStatus();
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Object resolveHeaderValue(Map<String, Object> headers, String expr) {
        if (headers == null || expr == null || expr.isEmpty()) {
            return null;
        }
        if (headers.containsKey(expr)) {
            return headers.get(expr);
        }
        String lower = expr.toLowerCase();
        for (Map.Entry<String, Object> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase().equals(lower)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 将提取值写入对应作用域；scope=asset 时需 entryKey 与 fieldPath。
     */
    @SuppressWarnings("unchecked")
    private static void writeExtractValue(FlowRunContext ctx, JSONObject ex, String scope, String name, Object val) {
        Object stored = val != null ? val : null;
        if ("flow".equals(scope)) {
            ctx.getFlow().put(name, stored);
            return;
        }
        if ("env".equals(scope)) {
            ctx.getEnv().put(name, stored);
            return;
        }
        if ("asset".equals(scope)) {
            String entryKey = ex.getString("entryKey");
            if (entryKey == null || entryKey.isEmpty()) {
                return;
            }
            Map<String, Object> assetRoot = ctx.getAsset();
            Object entry = assetRoot.get(entryKey);
            Map<String, Object> entryMap;
            if (entry instanceof Map<?, ?> m) {
                entryMap = (Map<String, Object>) m;
            } else {
                entryMap = new HashMap<>();
                assetRoot.put(entryKey, entryMap);
            }
            String fp = ex.getString("fieldPath");
            if (fp == null || fp.isEmpty()) {
                fp = name;
            }
            if (fp != null && !fp.isEmpty()) {
                entryMap.put(fp, stored);
            }
        }
    }
}
