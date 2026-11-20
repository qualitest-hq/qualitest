package com.qualitest.flow.inbound;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开放入站终态回包组装。
 * <p>
 * 从运行时 {@code out} 读出 HTTP 状态、响应头、响应体。
 * 合格形状必须含键 {@code body}；{@code status} 可缺省为 200；{@code headers} 可缺省。
 * {@code out} 为空或缺 {@code body} 时返回失败回包（HTTP 502 风格错包）。
 */
public final class OpenInboundOutFlush {

    private OpenInboundOutFlush() {
    }

    /**
     * 一次 flush 的结果：是否成功、HTTP 状态码、响应头、响应体、失败说明。
     */
    public record FlushResult(boolean ok, int status, Map<String, String> headers, Object body, String errorMessage) {
        /** 构造失败回包：JSON 体里带 {@code error} 字段 */
        public static FlushResult error(int status, String message) {
            Map<String, Object> errBody = new LinkedHashMap<>();
            errBody.put("error", message != null ? message : "开放入站回包失败");
            return new FlushResult(false, status, Map.of("Content-Type", "application/json;charset=UTF-8"),
                    errBody, message);
        }
    }

    /**
     * 把 {@code out} Map 转成可写入 HTTP 响应的结果。
     *
     * @param out 运行时出参；可空
     * @return {@code ok=true} 时按 status/headers/body 写响应；否则为错包
     */
    @SuppressWarnings("unchecked")
    public static FlushResult fromOut(Map<String, Object> out) {
        if (out == null || out.isEmpty()) {
            return FlushResult.error(502, "未写出参 out：请用 Assign 写入 scope=out（含 body）");
        }
        if (!out.containsKey("body")) {
            return FlushResult.error(502, "出参 out 缺少 body：请用 Assign 写入 out.body 或整对象含 body");
        }
        Object body = out.get("body");
        int status = 200;
        Object statusRaw = out.get("status");
        if (statusRaw instanceof Number n) {
            status = n.intValue();
        } else if (statusRaw != null) {
            try {
                status = Integer.parseInt(String.valueOf(statusRaw).trim());
            } catch (NumberFormatException ignored) {
                status = 200;
            }
        }
        Map<String, String> headers = new LinkedHashMap<>();
        Object headersRaw = out.get("headers");
        if (headersRaw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                headers.put(String.valueOf(e.getKey()), e.getValue() != null ? String.valueOf(e.getValue()) : "");
            }
        }
        if (!headers.containsKey("Content-Type") && !headers.containsKey("content-type")) {
            headers.put("Content-Type", "application/json;charset=UTF-8");
        }
        return new FlushResult(true, status, Collections.unmodifiableMap(headers), body, null);
    }
}
