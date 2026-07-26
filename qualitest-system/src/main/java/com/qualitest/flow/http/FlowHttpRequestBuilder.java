package com.qualitest.flow.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.flow.context.EnvUrlSupport;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.context.PlaceholderResolver;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;
import lombok.Getter;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 节点请求组装：解析占位符后产出 {@link com.qualitest.api.params.DebugHttpForwardParams}。
 * <p>
 * <b>project 模式</b>（{@link FlowHttpCallMode#PROJECT}）：
 * URL = env.baseUrl + 所绑 API 的 apiPath；
 * method / query / path / body 以 API 有效配置为底，再叠节点 data.requestValueOverrides；
 * 不读节点上的整份 requestConfig，也不读节点 apiPath。
 * <p>
 * <b>external 模式</b>（{@link FlowHttpCallMode#EXTERNAL}）：
 * URL = 节点 externalUrl；method / headers / body 在节点 data 上配置。
 * <p>
 * 正式 Run 使用 strict 占位符：未定义变量直接失败。
 */
public final class FlowHttpRequestBuilder {

    /** 正式 Run：未定义占位符抛错 */
    private static final PlaceholderResolver STRICT = PlaceholderResolver.strict();

    private FlowHttpRequestBuilder() {
    }

    /**
     * 按 callMode 分支组装 HTTP 请求。
     */
    public static BuiltHttpRequest build(FlowRunContext ctx, Map<String, Object> nodeData) {
        if (nodeData == null) {
            nodeData = Map.of();
        }
        String callMode = resolveCallMode(nodeData);
        if (FlowHttpCallMode.isExternal(callMode)) {
            return buildFromExternal(ctx, nodeData);
        }
        return buildFromProject(ctx, nodeData);
    }

    /**
     * project 模式：绑定 testProjectApiId，合并 API 资产定义。
     */
    public static BuiltHttpRequest buildFromProject(FlowRunContext ctx, TestProjectApi api, Map<String, Object> nodeData) {
        if (nodeData == null) {
            nodeData = Map.of();
        }
        Object apiIdObj = nodeData.get("testProjectApiId");
        if (apiIdObj == null || String.valueOf(apiIdObj).isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_UNBOUND, "未绑定 testProjectApiId");
        }

        JSONObject requestConfig = resolveRequestConfig(api, nodeData);
        String method = resolveMethod(requestConfig, nodeData);
        String apiPath = resolveApiPath(api);

        String baseUrl = EnvUrlSupport.ensureHttpSchemeForRequest(
                String.valueOf(ctx.getEnv().getOrDefault("baseUrl", ""))
        ).replaceAll("/$", "");
        if (baseUrl.isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_STEP_ERROR, "环境 baseUrl 未配置");
        }

        String urlPath = applyPathParams(apiPath, requestConfig, ctx);
        String query = buildQueryString(requestConfig, ctx);
        String fullUrl = baseUrl + urlPath + (query.isEmpty() ? "" : "?" + query);

        Map<String, String> headers = mergeHeaders(api, nodeData, ctx);
        DebugHttpForwardParams.DebugBodySpec bodySpec = buildBody(requestConfig, method, ctx, headers);

        return assembleBuiltRequest(method, fullUrl, headers, bodySpec, nodeData, FlowHttpCallMode.PROJECT);
    }

    private static BuiltHttpRequest buildFromProject(FlowRunContext ctx, Map<String, Object> nodeData) {
        Object apiIdObj = nodeData.get("testProjectApiId");
        if (apiIdObj == null || String.valueOf(apiIdObj).isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_UNBOUND, "未绑定 testProjectApiId");
        }
        return buildFromProject(ctx, null, nodeData);
    }

    /**
     * external 模式：使用节点配置的完整 URL，不加载 API 资产。
     */
    public static BuiltHttpRequest buildFromExternal(FlowRunContext ctx, Map<String, Object> nodeData) {
        if (nodeData == null) {
            nodeData = Map.of();
        }
        Object urlObj = nodeData.get("externalUrl");
        if (urlObj == null || String.valueOf(urlObj).isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, "外联 URL 未配置");
        }
        String resolvedUrl = STRICT.resolve(String.valueOf(urlObj), ctx).trim();
        ExternalUrlValidator.validate(resolvedUrl);

        String method = resolveExternalMethod(nodeData);
        Map<String, String> headers = mergeExternalHeaders(nodeData, ctx);
        DebugHttpForwardParams.DebugBodySpec bodySpec = buildExternalBody(nodeData, method, ctx, headers);

        return assembleBuiltRequest(method, resolvedUrl, headers, bodySpec, nodeData, FlowHttpCallMode.EXTERNAL);
    }

    private static BuiltHttpRequest assembleBuiltRequest(
            String method,
            String fullUrl,
            Map<String, String> headers,
            DebugHttpForwardParams.DebugBodySpec bodySpec,
            Map<String, Object> nodeData,
            String callMode) {
        Integer timeoutMs = resolveTimeout(nodeData);

        DebugHttpForwardParams params = DebugHttpForwardParams.builder()
                .method(method)
                .url(fullUrl)
                .headers(toHeaderPairs(headers))
                .timeoutMs(timeoutMs)
                .followRedirects(true)
                .allowInsecureTls(false)
                .body(bodySpec)
                .build();

        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("method", method);
        requestSnapshot.put("url", fullUrl);
        requestSnapshot.put("headers", new LinkedHashMap<>(headers));
        if (bodySpec != null) {
            requestSnapshot.put("body", bodySpec);
        }

        return new BuiltHttpRequest(params, method, fullUrl, requestSnapshot, callMode);
    }

    private static String resolveCallMode(Map<String, Object> nodeData) {
        Object raw = nodeData.get("callMode");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_CALLMODE, "HTTP 节点缺少 callMode");
        }
        String callMode = String.valueOf(raw).trim();
        if (!FlowHttpCallMode.isKnown(callMode)) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_CALLMODE, "HTTP 节点 callMode 无效：" + callMode);
        }
        return callMode;
    }

    private static String resolveExternalMethod(Map<String, Object> nodeData) {
        Object hm = nodeData.get("httpMethod");
        if (hm == null || String.valueOf(hm).isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_STEP_ERROR, "外联 HTTP 缺少 httpMethod");
        }
        return String.valueOf(hm).trim().toUpperCase();
    }

    private static Map<String, String> mergeExternalHeaders(Map<String, Object> nodeData, FlowRunContext ctx) {
        Map<String, String> headers = new LinkedHashMap<>();
        appendHeaderRows(headers, parseHeaderRows(nodeData.get("headers")), ctx);
        return headers;
    }

    private static DebugHttpForwardParams.DebugBodySpec buildExternalBody(
            Map<String, Object> nodeData, String method, FlowRunContext ctx, Map<String, String> headers) {
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return null;
        }
        Object bodyObj = nodeData.get("requestBody");
        if (bodyObj == null || String.valueOf(bodyObj).isBlank()) {
            return null;
        }
        String resolved = STRICT.resolve(String.valueOf(bodyObj), ctx);
        if (!hasHeader(headers, "Content-Type")) {
            headers.put("Content-Type", guessContentType(resolved));
        }
        DebugHttpForwardParams.DebugBodySpec spec = new DebugHttpForwardParams.DebugBodySpec();
        String trimmed = resolved.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            spec.setKind("json");
            spec.setRaw(resolved);
            try {
                if (trimmed.startsWith("{")) {
                    spec.setJson(JSON.parseObject(trimmed));
                }
            } catch (Exception e) {
                spec.setKind("raw");
                spec.setContentType(headers.get("Content-Type"));
            }
        } else if (resolved.contains("=") && !resolved.contains("{")) {
            spec.setKind("urlencoded");
            List<List<String>> fields = new ArrayList<>();
            for (String pair : resolved.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    fields.add(List.of(pair.substring(0, eq), pair.substring(eq + 1)));
                }
            }
            spec.setFields(fields);
        } else {
            spec.setKind("raw");
            spec.setRaw(resolved);
            spec.setContentType(headers.get("Content-Type"));
        }
        return spec;
    }

    private static String guessContentType(String body) {
        String trimmed = body != null ? body.trim() : "";
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "application/json";
        }
        if (body != null && body.contains("=") && !body.contains("{")) {
            return "application/x-www-form-urlencoded";
        }
        return "text/plain";
    }

    /** 从 externalUrl 提取 host/path 用于摘要展示 */
    public static String formatExternalSummary(String method, String externalUrl) {
        try {
            String resolved = externalUrl != null ? externalUrl.trim() : "";
            if (resolved.contains("{{")) {
                int slash = resolved.indexOf('/', resolved.indexOf("}}") + 2);
                String hostPart = slash > 0 ? resolved.substring(0, slash) : resolved;
                String pathPart = slash > 0 ? resolved.substring(slash) : "";
                return method + " ↗ " + hostPart + pathPart;
            }
            URI uri = new URI(resolved.startsWith("http") ? resolved : "https://" + resolved);
            String host = uri.getHost() != null ? uri.getHost() : resolved;
            String path = uri.getRawPath() != null && !uri.getRawPath().isBlank() ? uri.getRawPath() : "/";
            return method + " ↗ " + host + path;
        } catch (Exception e) {
            return method + " ↗ " + (externalUrl != null ? externalUrl : "—");
        }
    }

    /**
     * 合成真正发出的 requestConfig：
     * 以 API 有效配置为底，再叠节点 requestValueOverrides。
     * 忽略节点上残留的整份 requestConfig。
     */
    private static JSONObject resolveRequestConfig(TestProjectApi api, Map<String, Object> nodeData) {
        String base = "{}";
        if (api != null && api.getRequestConfig() != null && !api.getRequestConfig().isBlank()) {
            base = api.getRequestConfig();
        }
        Object overrides = nodeData != null ? nodeData.get("requestValueOverrides") : null;
        String overlaid = TestProjectApiEffectiveConfigResolver.overlayRequestValuesFromOverrides(base, overrides);
        JSONObject parsed = parseJsonObject(overlaid);
        return parsed != null ? parsed : new JSONObject();
    }

    private static String resolveMethod(JSONObject requestConfig, Map<String, Object> nodeData) {
        String method = requestConfig.getString("method");
        if (method == null || method.isBlank()) {
            Object hm = nodeData.get("httpMethod");
            method = hm != null ? String.valueOf(hm) : "GET";
        }
        return method.trim().toUpperCase();
    }

    /**
     * 解析请求路径：只用所绑 API 的 apiPath，忽略节点 data.apiPath。
     */
    private static String resolveApiPath(TestProjectApi api) {
        // project：路径始终跟所绑资产
        String path = null;
        if (api != null && api.getApiPath() != null) {
            path = api.getApiPath().trim();
        }
        if (path == null || path.isBlank()) {
            path = "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    private static String applyPathParams(String apiPath, JSONObject requestConfig, FlowRunContext ctx) {
        String urlPath = apiPath;
        JSONArray pathParams = requestConfig.getJSONArray("pathParams");
        if (pathParams == null) {
            return urlPath;
        }
        for (int i = 0; i < pathParams.size(); i++) {
            JSONObject row = pathParams.getJSONObject(i);
            if (row == null || Boolean.FALSE.equals(row.getBoolean("_enabled"))) {
                continue;
            }
            String name = row.getString("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            String val = STRICT.resolve(String.valueOf(row.getOrDefault("value", "")), ctx);
            urlPath = urlPath.replace("{" + name + "}", encode(val));
        }
        return urlPath;
    }

    private static String buildQueryString(JSONObject requestConfig, FlowRunContext ctx) {
        JSONArray params = requestConfig.getJSONArray("queryParams");
        if (params == null || params.isEmpty()) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            JSONObject row = params.getJSONObject(i);
            if (row == null || Boolean.FALSE.equals(row.getBoolean("_enabled"))) {
                continue;
            }
            String name = row.getString("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            String val = STRICT.resolve(String.valueOf(row.getOrDefault("value", "")), ctx);
            pairs.add(encode(name) + "=" + encode(val));
        }
        return String.join("&", pairs);
    }

    private static Map<String, String> mergeHeaders(TestProjectApi api, Map<String, Object> nodeData, FlowRunContext ctx) {
        Map<String, String> headers = new LinkedHashMap<>();
        appendHeaderRows(headers, parseHeaderRows(api != null ? api.getHeaders() : null), ctx);
        appendHeaderRows(headers, parseHeaderRows(nodeData.get("headers")), ctx);
        appendHeaderRows(headers, parseHeaderRows(api != null ? api.getCookies() : null), ctx);
        appendHeaderRows(headers, parseHeaderRows(nodeData.get("cookies")), ctx);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static void appendHeaderRows(Map<String, String> headers, List<Map<String, Object>> rows, FlowRunContext ctx) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (row == null || Boolean.FALSE.equals(row.get("_enabled"))) {
                continue;
            }
            String name = row.get("name") != null ? String.valueOf(row.get("name")).trim() : "";
            if (name.isEmpty() && row.get("key") != null) {
                name = String.valueOf(row.get("key")).trim();
            }
            if (name.isEmpty()) {
                continue;
            }
            String val = STRICT.resolve(String.valueOf(row.getOrDefault("value", "")), ctx);
            headers.put(name, val);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseHeaderRows(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        JSONObject obj = parseJsonObject(raw);
        if (obj == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : obj.keySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("_enabled", true);
            row.put("name", key);
            row.put("value", obj.getString(key));
            out.add(row);
        }
        return out;
    }

    private static DebugHttpForwardParams.DebugBodySpec buildBody(
            JSONObject requestConfig, String method, FlowRunContext ctx, Map<String, String> headers) {
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return null;
        }
        JSONObject body = requestConfig.getJSONObject("body");
        if (body == null) {
            return null;
        }
        String mode = body.getString("mode");
        if (mode == null || mode.isBlank() || "none".equals(mode)) {
            return null;
        }
        return switch (mode) {
            case "json" -> buildJsonBody(body, ctx, headers);
            case "x-www-form-urlencoded", "urlencoded" -> buildUrlencodedBody(body, ctx, headers);
            case "text", "xml" -> buildRawBody(body, ctx, mode, headers);
            default -> null;
        };
    }

    private static DebugHttpForwardParams.DebugBodySpec buildJsonBody(
            JSONObject body, FlowRunContext ctx, Map<String, String> headers) {
        JSONObject jsonPart = body.getJSONObject("json");
        String example = jsonPart != null ? jsonPart.getString("example") : null;
        if (example == null) {
            example = "{}";
        }
        String resolved = STRICT.resolve(example, ctx);
        if (!hasHeader(headers, "Content-Type")) {
            headers.put("Content-Type", "application/json");
        }
        DebugHttpForwardParams.DebugBodySpec spec = new DebugHttpForwardParams.DebugBodySpec();
        spec.setKind("json");
        spec.setRaw(resolved);
        try {
            spec.setJson(JSON.parseObject(resolved));
        } catch (Exception e) {
            spec.setJson(null);
        }
        return spec;
    }

    private static DebugHttpForwardParams.DebugBodySpec buildUrlencodedBody(
            JSONObject body, FlowRunContext ctx, Map<String, String> headers) {
        JSONArray rows = body.getJSONArray("urlencoded");
        List<List<String>> fields = new ArrayList<>();
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (row == null || Boolean.FALSE.equals(row.getBoolean("_enabled"))) {
                    continue;
                }
                String name = row.getString("name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                String val = STRICT.resolve(String.valueOf(row.getOrDefault("value", "")), ctx);
                fields.add(List.of(name, val));
            }
        }
        if (!hasHeader(headers, "Content-Type")) {
            headers.put("Content-Type", "application/x-www-form-urlencoded");
        }
        DebugHttpForwardParams.DebugBodySpec spec = new DebugHttpForwardParams.DebugBodySpec();
        spec.setKind("urlencoded");
        spec.setFields(fields);
        return spec;
    }

    private static DebugHttpForwardParams.DebugBodySpec buildRawBody(
            JSONObject body, FlowRunContext ctx, String mode, Map<String, String> headers) {
        String text = body.getString("text");
        if (text == null) {
            text = "";
        }
        String resolved = STRICT.resolve(text, ctx);
        if (!hasHeader(headers, "Content-Type")) {
            headers.put("Content-Type", "xml".equals(mode) ? "application/xml" : "text/plain");
        }
        DebugHttpForwardParams.DebugBodySpec spec = new DebugHttpForwardParams.DebugBodySpec();
        spec.setKind("raw");
        spec.setRaw(resolved);
        spec.setContentType(headers.get("Content-Type"));
        return spec;
    }

    private static Integer resolveTimeout(Map<String, Object> nodeData) {
        Object ms = nodeData.get("timeoutMs");
        if (ms instanceof Number n) {
            return n.intValue();
        }
        if (ms != null) {
            try {
                return Integer.parseInt(String.valueOf(ms));
            } catch (NumberFormatException ignored) {
                return 30000;
            }
        }
        return 30000;
    }

    private static List<DebugHttpForwardParams.HeaderPair> toHeaderPairs(Map<String, String> headers) {
        List<DebugHttpForwardParams.HeaderPair> pairs = new ArrayList<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            pairs.add(new DebugHttpForwardParams.HeaderPair(e.getKey(), e.getValue()));
        }
        return pairs;
    }

    private static boolean hasHeader(Map<String, String> headers, String name) {
        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static JSONObject parseJsonObject(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof JSONObject jo) {
            return jo;
        }
        if (raw instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        try {
            return JSON.parseObject(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** build 方法的返回值：转发参数 + 写入 step_details 的请求快照字段 */
    @Getter
    public static class BuiltHttpRequest {
        private final DebugHttpForwardParams forwardParams;
        private final String method;
        private final String url;
        private final Map<String, Object> requestSnapshot;
        private final String callMode;

        public BuiltHttpRequest(DebugHttpForwardParams forwardParams, String method, String url,
                                Map<String, Object> requestSnapshot, String callMode) {
            this.forwardParams = forwardParams;
            this.method = method;
            this.url = url;
            this.requestSnapshot = requestSnapshot;
            this.callMode = callMode != null ? callMode : FlowHttpCallMode.PROJECT;
        }
    }
}
