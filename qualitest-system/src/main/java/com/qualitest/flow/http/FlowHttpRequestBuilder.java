package com.qualitest.flow.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.util.AuthHeaderResolver;
import com.qualitest.common.config.QualitestConfig;
import com.qualitest.common.utils.file.FileUtils;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 节点请求组装：解析占位符后产出 {@link com.qualitest.api.params.DebugHttpForwardParams}。
 * <p>
 * <b>project 模式</b>（{@link FlowHttpCallMode#PROJECT}）：
 * URL = env.baseUrl + 所绑 API 的 apiPath；
 * method / query / path 以 API 有效配置为底（可含 paramDefaults），再叠节点 data.requestValueOverrides；
 * <b>body 测值不退回</b>接口资产的 bodyExample / 结构层 example，仅节点 overrides 可写入；
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
     * 以 API 有效配置为底（先剥掉 body.json.example，避免退回接口默认 body），
     * 再叠节点 requestValueOverrides（可整段写入 bodyExample）。
     * 忽略节点上残留的整份 requestConfig。
     */
    private static JSONObject resolveRequestConfig(TestProjectApi api, Map<String, Object> nodeData) {
        String base = "{}";
        if (api != null && api.getRequestConfig() != null && !api.getRequestConfig().isBlank()) {
            base = api.getRequestConfig();
        }
        // 跑流不退回接口 TV / 结构层 body 默认；场景 body 只认节点 overrides
        base = TestProjectApiEffectiveConfigResolver.stripBodyExample(base);
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

        Object rawNodeHeaders = nodeData != null ? nodeData.get("headers") : null;
        List<Map<String, Object>> nodeHeaderRows = parseHeaderRows(rawNodeHeaders);
        if (api != null && ctx != null) {
            AuthHeaderResolver.ResolvedAuthHeader resolved = AuthHeaderResolver.resolve(
                    api.getAuthConfig(),
                    ctx.getProjectAuthConfig(),
                    resolveApiPath(api));
            // 按当前项目配置刷新/补齐托管头（显式非托管头不改）
            nodeHeaderRows = AuthHeaderResolver.applyToHeaderRows(nodeHeaderRows, resolved).headers();
        }
        appendHeaderRows(headers, nodeHeaderRows, ctx);
        appendHeaderRows(headers, parseHeaderRows(api != null ? api.getCookies() : null), ctx);
        appendHeaderRows(headers, parseHeaderRows(nodeData != null ? nodeData.get("cookies") : null), ctx);
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
            case "form-data", "formData", "multipart" -> buildFormDataBody(body, ctx);
            case "binary" -> buildBinaryBody(body, ctx, headers);
            default -> null;
        };
    }

    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;

    /**
     * 组装 form-data 请求体：普通字段进 fields；type=file 的行按路径读盘后进 files（真文件 part）。
     */
    private static DebugHttpForwardParams.DebugBodySpec buildFormDataBody(JSONObject body, FlowRunContext ctx) {
        JSONArray rows = body.getJSONArray("formData");
        List<List<String>> fields = new ArrayList<>();
        List<DebugHttpForwardParams.DebugBodySpec.FormFile> files = new ArrayList<>();
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
                String type = row.getString("type");
                String rawVal = String.valueOf(row.getOrDefault("value", ""));
                String resolved = STRICT.resolve(rawVal, ctx);
                if (type != null && "file".equalsIgnoreCase(type.trim())) {
                    files.add(loadFormFile(name, resolved));
                } else {
                    fields.add(List.of(name, resolved));
                }
            }
        }
        DebugHttpForwardParams.DebugBodySpec spec = new DebugHttpForwardParams.DebugBodySpec();
        spec.setKind("formData");
        spec.setFields(fields);
        spec.setFiles(files);
        return spec;
    }

    private static DebugHttpForwardParams.DebugBodySpec buildBinaryBody(
            JSONObject body, FlowRunContext ctx, Map<String, String> headers) {
        JSONObject binary = body.getJSONObject("binary");
        String pathValue = "";
        if (binary != null) {
            Object v = binary.get("value");
            if (v == null) {
                v = binary.get("filePath");
            }
            if (v != null) {
                pathValue = String.valueOf(v);
            }
        }
        if (pathValue.isBlank()) {
            pathValue = body.getString("text");
        }
        if (pathValue == null) {
            pathValue = "";
        }
        String resolved = STRICT.resolve(pathValue, ctx);
        Path file = resolveReadableFile(resolved);
        byte[] data = readFileBytes(file);
        String fileName = file.getFileName().toString();
        String contentType = guessFileContentType(fileName);
        if (!hasHeader(headers, "Content-Type")) {
            headers.put("Content-Type", contentType);
        }
        DebugHttpForwardParams.DebugBodySpec spec = new DebugHttpForwardParams.DebugBodySpec();
        spec.setKind("binary");
        spec.setFileName(fileName);
        spec.setContentType(contentType);
        spec.setRaw(Base64.getEncoder().encodeToString(data));
        return spec;
    }

    /**
     * 按已解析路径读本地文件，编成 form-data 文件 part（字段名、文件名、Content-Type、base64 内容）。
     */
    private static DebugHttpForwardParams.DebugBodySpec.FormFile loadFormFile(String fieldName, String resolvedPath) {
        if (resolvedPath == null || resolvedPath.isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_STEP_ERROR,
                    "form-data 文件字段 " + fieldName + " 未配置路径");
        }
        Path file = resolveReadableFile(resolvedPath.trim());
        byte[] data = readFileBytes(file);
        String fileName = file.getFileName().toString();
        DebugHttpForwardParams.DebugBodySpec.FormFile formFile =
                new DebugHttpForwardParams.DebugBodySpec.FormFile();
        formFile.setName(fieldName);
        formFile.setFileName(fileName);
        formFile.setContentType(guessFileContentType(fileName));
        formFile.setBase64(Base64.getEncoder().encodeToString(data));
        return formFile;
    }

    /**
     * 把测参路径解析成可读的本地文件。
     * 依次尝试：绝对路径 → 去掉 /profile 前缀后拼到 profile 根目录 → 相对 profile → 相对 upload 目录。
     */
    private static Path resolveReadableFile(String resolvedPath) {
        Path direct = Path.of(resolvedPath);
        if (Files.isRegularFile(direct)) {
            return direct.toAbsolutePath().normalize();
        }
        String profile = QualitestConfig.getProfile();
        if (profile == null || profile.isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_STEP_ERROR,
                    "找不到上传文件：" + resolvedPath);
        }
        // 存储路径常带 /profile 前缀，需剥掉后再拼到 profile 根目录
        Path viaProfilePrefix = tryResolveUnderProfile(profile, FileUtils.stripPrefix(resolvedPath));
        if (viaProfilePrefix != null) {
            return viaProfilePrefix;
        }
        Path underProfile = Path.of(profile, resolvedPath);
        if (Files.isRegularFile(underProfile)) {
            return underProfile.toAbsolutePath().normalize();
        }
        Path underUpload = Path.of(QualitestConfig.getUploadPath(), resolvedPath);
        if (Files.isRegularFile(underUpload)) {
            return underUpload.toAbsolutePath().normalize();
        }
        throw new FlowExecutionException(FlowErrorCode.TF_STEP_ERROR,
                "找不到上传文件：" + resolvedPath);
    }

    /**
     * 在 profile 根目录下按相对路径找文件。
     * @param stripped 已去掉 /profile 前缀的路径（可带或不带前导斜杠）
     * @return 存在则返回规范化绝对路径，否则 null
     */
    private static Path tryResolveUnderProfile(String profile, String stripped) {
        if (stripped == null || stripped.isBlank()) {
            return null;
        }
        String relative = stripped.startsWith("/") || stripped.startsWith("\\")
                ? stripped.substring(1)
                : stripped;
        if (relative.isBlank()) {
            return null;
        }
        Path path = Path.of(profile, relative);
        return Files.isRegularFile(path) ? path.toAbsolutePath().normalize() : null;
    }

    private static byte[] readFileBytes(Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            if (data.length > MAX_FILE_BYTES) {
                throw new FlowExecutionException(FlowErrorCode.TF_STEP_ERROR,
                        "文件超过 " + (MAX_FILE_BYTES / 1024 / 1024) + "MB 限制：" + file);
            }
            return data;
        } catch (FlowExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowExecutionException(FlowErrorCode.TF_STEP_ERROR,
                    "读取上传文件失败：" + file + " — " + e.getMessage());
        }
    }

    private static String guessFileContentType(String fileName) {
        String lower = fileName != null ? fileName.toLowerCase() : "";
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".avi")) {
            return "video/x-msvideo";
        }
        return "application/octet-stream";
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
