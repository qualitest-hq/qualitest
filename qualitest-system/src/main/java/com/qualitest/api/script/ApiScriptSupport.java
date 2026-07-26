package com.qualitest.api.script;

import com.alibaba.fastjson2.JSON;
import com.qualitest.api.params.ApiRequestScriptExecuteParams;
import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.result.ApiRequestScriptExecuteResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API 脚本上下文构建、转发参数互转与 REST DTO 映射。
 */
public final class ApiScriptSupport {

    private ApiScriptSupport() {
    }

    // --- context ---

    public static ApiScriptContext create(
            ApiScriptPhase phase,
            Map<String, Object> variables,
            Map<String, Object> environment,
            Map<String, Object> globals,
            DebugHttpForwardParams forwardParams,
            ApiScriptContext.ResponseSnapshot response
    ) {
        ApiScriptContext ctx = new ApiScriptContext();
        ctx.setPhase(phase);
        ctx.mergeScopeMaps(variables, environment, globals);
        ctx.setRequest(fromForwardParams(forwardParams));
        if (phase == ApiScriptPhase.POST) {
            ctx.setResponse(response);
        }
        return ctx;
    }

    public static ApiScriptContext.RequestSnapshot fromForwardParams(DebugHttpForwardParams params) {
        ApiScriptContext.RequestSnapshot request = new ApiScriptContext.RequestSnapshot();
        if (params == null) {
            return request;
        }
        request.setMethod(params.getMethod());
        request.setUrl(params.getUrl());
        if (params.getHeaders() != null) {
            for (DebugHttpForwardParams.HeaderPair pair : params.getHeaders()) {
                if (pair != null && pair.getName() != null) {
                    request.getHeaders().put(pair.getName(), pair.getValue() != null ? pair.getValue() : "");
                }
            }
        }
        request.setBody(bodySpecToMap(params.getBody()));
        return request;
    }

    public static ApiScriptContext.ResponseSnapshot fromForwardResult(
            int status,
            String statusText,
            Map<String, String> headers,
            String bodyText,
            Long durationMs
    ) {
        ApiScriptContext.ResponseSnapshot response = new ApiScriptContext.ResponseSnapshot();
        response.setCode(status);
        response.setStatusText(statusText);
        response.setBodyText(bodyText);
        response.setDurationMs(durationMs);
        if (headers != null) {
            response.setHeadersFromMap(new LinkedHashMap<>(headers));
        }
        return response;
    }

    public static ApiScriptContext.ResponseSnapshot fromDebugResponseMap(Map<String, Object> raw) {
        if (raw == null) {
            return new ApiScriptContext.ResponseSnapshot();
        }
        ApiScriptContext.ResponseSnapshot response = new ApiScriptContext.ResponseSnapshot();
        Object status = raw.get("status");
        if (status == null) {
            status = raw.get("code");
        }
        response.setCode(status instanceof Number n ? n.intValue() : 0);
        Object statusText = raw.get("statusText");
        response.setStatusText(statusText != null ? String.valueOf(statusText) : "");
        Object bodyText = raw.get("bodyText");
        response.setBodyText(bodyText != null ? String.valueOf(bodyText) : "");
        Object durationMs = raw.get("durationMs");
        if (durationMs instanceof Number n) {
            response.setDurationMs(n.longValue());
        }
        Object headers = raw.get("headers");
        if (headers instanceof Map<?, ?> map) {
            response.setHeadersFromMap(map);
        }
        return response;
    }

    /** 供调试台 REST：从前端 built 结构构建 request 快照 */
    public static ApiScriptContext.RequestSnapshot fromDebugBuilt(
            String method,
            String url,
            Map<String, String> headers,
            Object data
    ) {
        ApiScriptContext.RequestSnapshot request = new ApiScriptContext.RequestSnapshot();
        request.setMethod(method);
        request.setUrl(url);
        request.setHeadersFromMap(headers);
        Map<String, Object> body = new LinkedHashMap<>();
        if (data == null) {
            body.put("kind", "none");
        } else if (data instanceof String text) {
            body.put("kind", "raw");
            body.put("raw", text);
        } else {
            body.put("kind", "raw");
            body.put("raw", JSON.toJSONString(data));
        }
        request.setBody(body);
        return request;
    }

    // --- forward ---

    public static void applyToForwardParams(ApiScriptContext.RequestSnapshot request, DebugHttpForwardParams params) {
        if (request == null || params == null) {
            return;
        }
        if (request.getMethod() != null && !request.getMethod().isBlank()) {
            params.setMethod(request.getMethod().trim().toUpperCase());
        }
        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            params.setUrl(request.getUrl().trim());
        }
        params.setHeaders(toHeaderPairs(request.getHeaders()));
        params.setBody(mapToBodySpec(request.getBody()));
    }

    public static List<DebugHttpForwardParams.HeaderPair> toHeaderPairs(Map<String, String> headers) {
        List<DebugHttpForwardParams.HeaderPair> pairs = new ArrayList<>();
        if (headers == null) {
            return pairs;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            pairs.add(new DebugHttpForwardParams.HeaderPair(entry.getKey(), entry.getValue()));
        }
        return pairs;
    }

    public static Map<String, Object> bodySpecToMap(DebugHttpForwardParams.DebugBodySpec body) {
        if (body == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", body.getKind());
        map.put("raw", body.getRaw());
        map.put("contentType", body.getContentType());
        map.put("json", body.getJson());
        map.put("fields", body.getFields());
        return map;
    }

    public static DebugHttpForwardParams.DebugBodySpec mapToBodySpec(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        DebugHttpForwardParams.DebugBodySpec spec = new DebugHttpForwardParams.DebugBodySpec();
        Object kind = body.get("kind");
        spec.setKind(kind != null ? String.valueOf(kind) : null);
        Object raw = body.get("raw");
        spec.setRaw(raw != null ? String.valueOf(raw) : null);
        Object contentType = body.get("contentType");
        spec.setContentType(contentType != null ? String.valueOf(contentType) : null);
        Object json = body.get("json");
        if (json instanceof Map<?, ?> jsonMap) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : jsonMap.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            spec.setJson(converted);
        }
        Object fields = body.get("fields");
        if (fields instanceof List<?> list) {
            List<List<String>> converted = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof List<?> row) {
                    List<String> pair = new ArrayList<>();
                    for (Object cell : row) {
                        pair.add(cell != null ? String.valueOf(cell) : "");
                    }
                    converted.add(pair);
                }
            }
            spec.setFields(converted);
        }
        return spec;
    }

    // --- rest ---

    public static ApiScriptContext toContext(
            ApiScriptPhase phase,
            ApiRequestScriptExecuteParams params
    ) {
        ApiScriptContext ctx = new ApiScriptContext();
        ctx.setPhase(phase);
        if (params == null) {
            return ctx;
        }
        ctx.mergeScopeMaps(params.getVariables(), params.getEnvironment(), params.getGlobals());
        ctx.setRequest(fromRequestSnapshot(params.getRequest()));
        if (phase == ApiScriptPhase.POST && params.getResponse() != null) {
            ctx.setResponse(fromResponseSnapshot(params.getResponse()));
        }
        return ctx;
    }

    public static DebugHttpForwardParams toForwardParams(ApiRequestScriptExecuteParams.ApiRequestSnapshot snapshot) {
        if (snapshot == null) {
            return DebugHttpForwardParams.builder().method("GET").url("").build();
        }
        return DebugHttpForwardParams.builder()
                .method(snapshot.getMethod() != null ? snapshot.getMethod() : "GET")
                .url(snapshot.getUrl() != null ? snapshot.getUrl() : "")
                .headers(toHeaderPairs(snapshot.getHeaders()))
                .body(mapToBodySpec(snapshot.getBody()))
                .timeoutMs(60000)
                .followRedirects(true)
                .allowInsecureTls(false)
                .build();
    }

    public static ApiRequestScriptExecuteResult toResult(ApiScriptExecutionResult result) {
        ApiScriptContext ctx = result.getContext();
        Map<String, Object> scope = ctx != null ? ctx.exportScopeMaps() : Map.of();
        return ApiRequestScriptExecuteResult.builder()
                .success(result.isSuccess())
                .errorCode(result.getErrorCode() != null ? result.getErrorCode().getCode() : null)
                .errorMessage(result.getErrorMessage())
                .logs(result.getLogs())
                .tests(result.getTests())
                .writes(result.getWrites())
                .variables(copy(scope.get("variables")))
                .environment(copy(scope.get("environment")))
                .globals(copy(scope.get("globals")))
                .request(ctx != null && ctx.getRequest() != null ? ctx.getRequest().toMap() : Map.of())
                .build();
    }

    private static ApiScriptContext.RequestSnapshot fromRequestSnapshot(
            ApiRequestScriptExecuteParams.ApiRequestSnapshot snapshot
    ) {
        ApiScriptContext.RequestSnapshot request = new ApiScriptContext.RequestSnapshot();
        if (snapshot == null) {
            return request;
        }
        request.setMethod(snapshot.getMethod());
        request.setUrl(snapshot.getUrl());
        request.setHeadersFromMap(snapshot.getHeaders());
        request.setBody(snapshot.getBody() != null ? new LinkedHashMap<>(snapshot.getBody()) : new LinkedHashMap<>());
        return request;
    }

    private static ApiScriptContext.ResponseSnapshot fromResponseSnapshot(
            ApiRequestScriptExecuteParams.ApiResponseSnapshot snapshot
    ) {
        ApiScriptContext.ResponseSnapshot response = new ApiScriptContext.ResponseSnapshot();
        Integer status = snapshot.getStatus() != null ? snapshot.getStatus() : snapshot.getCode();
        response.setCode(status != null ? status : 0);
        response.setStatusText(snapshot.getStatusText());
        response.setBodyText(snapshot.getBodyText());
        response.setDurationMs(snapshot.getDurationMs());
        response.setHeadersFromMap(snapshot.getHeaders());
        return response;
    }

    private static Map<String, Object> copy(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        return new LinkedHashMap<>();
    }
}
