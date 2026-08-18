package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignApiSummarizer;
import com.qualitest.api.util.LoginExtractSuggestor;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.http.FlowHttpNodePathSupport;
import com.qualitest.flow.http.HttpNodeRequestValueOverridesSupport;
import com.qualitest.flow.http.SuccessCheckResolver;
import com.qualitest.project.domain.TestProjectApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 设计 patch 落盘前，规范化 HTTP 节点 data。
 * <p>
 * project 模式：
 * <ul>
 *   <li>测值写入 requestValueOverrides（只保留相对资产默认不同的项）</li>
 *   <li>删除整份 requestConfig、临时 requestBody、apiPath</li>
 *   <li>必要时从 API 补全 httpMethod</li>
 * </ul>
 * 另处理 extracts 路径规范化、successCheck 默认 mode。
 * external 模式只处理 extracts 与 successCheck。
 */
public final class FlowDesignHttpNodeNormalizer {

    private FlowDesignHttpNodeNormalizer() {
    }

    /**
     * 空 callMode 写成 project；非法非空串留给图校验。
     */
    public static void ensureCallModeDefault(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object raw = data.get("callMode");
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            data.put("callMode", FlowHttpCallMode.PROJECT);
        }
    }

    /**
     * 无 API 上下文时的轻量规范化：callMode / extracts / successCheck。
     * Staging draft 再规范化时使用，不做 overrides 差分。
     */
    public static void normalizeWithoutApi(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        ensureCallModeDefault(data);
        normalizeExtracts(data);
        ensureSuccessCheckDefault(data);
    }

    /**
     * 规范化 HTTP 节点 data（就地修改）。
     *
     * @param data            节点 data
     * @param api             已绑定的项目接口；未绑定或外联时为 null
     * @param projectAuthJson 项目鉴权 JSON；用于按 loginHint / 可用 schema 对齐登录 extract
     */
    public static void normalize(Map<String, Object> data, TestProjectApi api, String projectAuthJson) {
        if (data == null) {
            return;
        }
        ensureCallModeDefault(data);
        normalizeExtracts(data);
        alignLoginExtract(data, api, projectAuthJson);
        ensureSuccessCheckDefault(data);

        String callMode = data.get("callMode") != null ? String.valueOf(data.get("callMode")).trim() : "";
        if (FlowHttpCallMode.isExternal(callMode)) {
            return;
        }
        if (!FlowHttpCallMode.isProject(callMode) && data.get("testProjectApiId") == null) {
            return;
        }

        syncHttpMethodFromApi(data, api);

        JSONObject overrides = HttpNodeRequestValueOverridesSupport.buildDiffOverrides(
                data.get("requestValueOverrides"),
                data.get("requestConfig"),
                data.get("requestBody"),
                api);
        Map<String, Object> persist = HttpNodeRequestValueOverridesSupport.toPersistMap(overrides);
        if (persist != null) {
            data.put("requestValueOverrides", persist);
        } else {
            data.remove("requestValueOverrides");
        }

        data.remove("requestConfig");
        data.remove("requestBody");
        FlowHttpNodePathSupport.stripNodeApiPath(data);
    }

    /** 无项目鉴权 JSON 时规范化：不自动补登录 extract。 */
    public static void normalize(Map<String, Object> data, TestProjectApi api) {
        normalize(data, api, null);
    }

    /**
     * 登录/注册类接口：仅当 loginHint 或响应 schema 能确定 name+expr 时，
     * 空 extracts 补一行；已有「凭证类」行（token 名 + token 路径）则对齐到建议。
     * 自定义路径不改；无法确定 expr 时不编 JsonPath。
     */
    static void alignLoginExtract(
            Map<String, Object> data, TestProjectApi api, String projectAuthJson) {
        if (data == null || api == null
                || !LoginExtractSuggestor.hasCredentialLoginHint(projectAuthJson, null, api.getApiPath())) {
            return;
        }
        JSONObject schema = FlowDesignApiSummarizer.summarizeResponse(api.getResponseConfig());
        LoginExtractSuggestor.Suggestion suggestion = LoginExtractSuggestor.suggest(
                projectAuthJson, api.getApiPath(), schema);
        if (suggestion == null) {
            return;
        }
        Object raw = data.get("extracts");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            data.put("extracts", List.of(suggestion.toExtractRow()));
            return;
        }
        List<Object> next = new ArrayList<>();
        boolean aligned = false;
        for (Object item : list) {
            JSONObject row = toJsonObject(item);
            if (row == null) {
                continue;
            }
            if (!aligned && LoginExtractSuggestor.isCredentialLikeExtract(row)) {
                JSONObject alignedRow = new JSONObject(suggestion.toExtractRow());
                copyOptionalExtractFields(row, alignedRow);
                next.add(alignedRow);
                aligned = true;
                continue;
            }
            next.add(row);
        }
        if (aligned) {
            data.put("extracts", next);
        }
    }

    /**
     * 节点未写 httpMethod 时，从 API 的 requestConfig.method 补上。
     */
    private static void syncHttpMethodFromApi(Map<String, Object> data, TestProjectApi api) {
        Object existing = data.get("httpMethod");
        if (existing != null && !String.valueOf(existing).isBlank()) {
            data.put("httpMethod", String.valueOf(existing).trim().toUpperCase(Locale.ROOT));
            return;
        }
        if (api == null || api.getRequestConfig() == null || api.getRequestConfig().isBlank()) {
            return;
        }
        try {
            JSONObject rc = JSON.parseObject(api.getRequestConfig());
            if (rc == null) {
                return;
            }
            String method = rc.getString("method");
            if (method != null && !method.isBlank()) {
                data.put("httpMethod", method.trim().toUpperCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
            // keep unset
        }
    }

    /**
     * 为缺失的 successCheck 写入默认 mode：
     * project → inherit（按项目约定校验业务码）；external → off（不校验）。
     * 节点已配置 mode 时不覆盖。
     */
    static void ensureSuccessCheckDefault(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Object existing = data.get("successCheck");
        if (existing instanceof Map<?, ?> map && map.get("mode") != null
                && !String.valueOf(map.get("mode")).isBlank()) {
            return;
        }
        String callMode = data.get("callMode") != null ? String.valueOf(data.get("callMode")).trim() : "";
        String mode = FlowHttpCallMode.isExternal(callMode)
                ? SuccessCheckResolver.MODE_OFF
                : SuccessCheckResolver.MODE_INHERIT;
        Map<String, Object> successCheck = new LinkedHashMap<>();
        successCheck.put("mode", mode);
        data.put("successCheck", successCheck);
    }

    /**
     * 规范化 extracts 列表：补全 from/scope/name，把旧字段 value/path 转成 expr。
     * 不猜测补 $.data 前缀。语义健康检查比对抽取路径前也会调用。
     */
    public static void normalizeExtracts(Map<String, Object> data) {
        Object raw = data.get("extracts");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        JSONArray normalized = new JSONArray();
        for (Object item : list) {
            JSONObject row = toJsonObject(item);
            if (row == null) {
                continue;
            }
            JSONObject next = normalizeExtractRow(row);
            if (next != null) {
                normalized.add(next);
            }
        }
        if (!normalized.isEmpty()) {
            data.put("extracts", normalized);
        }
    }

    private static JSONObject normalizeExtractRow(JSONObject row) {
        String name = defaultString(row.getString("name"), row.getString("entryKey"));
        String expr = row.getString("expr");
        if (expr == null || expr.isBlank()) {
            Object legacy = row.get("value");
            if (legacy == null) {
                legacy = row.get("path");
            }
            if (legacy != null && !String.valueOf(legacy).isBlank()) {
                expr = convertLegacyExtractExpr(String.valueOf(legacy));
            }
        } else {
            expr = convertLegacyExtractExpr(expr);
        }
        if (name == null || name.isBlank() || expr == null || expr.isBlank()) {
            return null;
        }
        JSONObject next = new JSONObject();
        next.put("from", defaultString(row.getString("from"), "body"));
        next.put("expr", expr);
        next.put("scope", defaultString(row.getString("scope"), "flow"));
        next.put("name", name.trim());
        copyOptionalExtractFields(row, next);
        return next;
    }

    private static void copyOptionalExtractFields(JSONObject from, JSONObject to) {
        String entryKey = from.getString("entryKey");
        if (entryKey != null) {
            to.put("entryKey", entryKey);
        }
        String fieldPath = from.getString("fieldPath");
        if (fieldPath != null) {
            to.put("fieldPath", fieldPath);
        }
    }

    /**
     * 将旧式提取路径转为 $.a.b 形式。
     */
    static String convertLegacyExtractExpr(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.startsWith("$.")) {
            return text;
        }
        if (text.startsWith("http.body.")) {
            return "$." + text.substring("http.body.".length());
        }
        if (text.startsWith("responses.")) {
            return "$." + text.substring("responses.".length());
        }
        if (text.startsWith("response.")) {
            return "$." + text.substring("response.".length());
        }
        if (text.startsWith("body.")) {
            return "$." + text.substring("body.".length());
        }
        if (!text.startsWith("$") && !text.contains("{{")) {
            return "$." + text;
        }
        return text;
    }

    private static JSONObject toJsonObject(Object raw) {
        if (raw instanceof JSONObject obj) {
            return obj;
        }
        if (raw instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return JSON.parseObject(text);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
