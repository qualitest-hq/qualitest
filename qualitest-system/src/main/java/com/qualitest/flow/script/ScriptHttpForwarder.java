package com.qualitest.flow.script;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.http.FlowHttpRequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Script 节点 {@code ctx.http(options)} 的 HTTP 转发实现。
 * <p>
 * 将 options 转为外联模式请求并转发；占位符严格解析。
 * 出站前会校验外联 URL 协议/主机，以及转发层出口白名单（若已开启）。
 */
public final class ScriptHttpForwarder {

    private ScriptHttpForwarder() {
    }

    /**
     * @param opts {@code { method, url, headers?, body?, timeoutMs? }}
     * @return {@code { ok, status, headers, body, durationMs, error? }}
     */
    public static Map<String, Object> forward(
            FlowRunContext ctx,
            Map<String, Object> opts,
            IDebugHttpForwardService forwardService
    ) {
        if (forwardService == null) {
            throw new IllegalStateException("ctx.http 不可用");
        }
        if (opts == null || opts.isEmpty()) {
            throw new IllegalArgumentException("ctx.http 参数不能为空");
        }
        Object urlObj = opts.get("url");
        if (urlObj == null || String.valueOf(urlObj).isBlank()) {
            throw new IllegalArgumentException("ctx.http 缺少 url");
        }
        String method = opts.get("method") != null
                ? String.valueOf(opts.get("method")).trim().toUpperCase(Locale.ROOT)
                : "GET";

        Map<String, Object> nodeData = new LinkedHashMap<>();
        nodeData.put("externalUrl", String.valueOf(urlObj).trim());
        nodeData.put("httpMethod", method);
        nodeData.put("headers", toHeaderRows(opts.get("headers")));
        Object body = opts.get("body");
        if (body != null && !String.valueOf(body).isBlank()) {
            nodeData.put("requestBody", body instanceof String s ? s : ScriptValueConverter.jsonStringify(
                    ScriptValueConverter.fromGuest(body)));
        }
        Object timeoutMs = opts.get("timeoutMs");
        if (timeoutMs != null) {
            nodeData.put("timeoutMs", timeoutMs);
        }

        FlowHttpRequestBuilder.BuiltHttpRequest built = FlowHttpRequestBuilder.buildFromExternal(ctx, nodeData);
        long t0 = System.currentTimeMillis();
        DebugHttpForwardResult result = forwardService.forward(built.getForwardParams());
        long durationMs = System.currentTimeMillis() - t0;

        int status = result.getStatus() != null ? result.getStatus() : 0;
        boolean ok = result.getError() == null && status >= 200 && status < 300;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", ok);
        response.put("status", status);
        response.put("headers", result.getResponseHeaders() != null ? result.getResponseHeaders() : Map.of());
        response.put("body", result.getBodyText() != null ? result.getBodyText() : "");
        response.put("durationMs", durationMs);
        if (result.getError() != null) {
            response.put("error", result.getError());
        }
        return response;
    }

    private static List<Map<String, Object>> toHeaderRows(Object headersObj) {
        if (!(headersObj instanceof Map<?, ?> headerMap) || headerMap.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            rows.add(Map.of(
                    "_enabled", true,
                    "name", String.valueOf(entry.getKey()),
                    "value", entry.getValue() != null ? String.valueOf(entry.getValue()) : ""
            ));
        }
        return rows;
    }
}
