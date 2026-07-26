package com.qualitest.flow.session;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Run 级 Cookie Jar：单次测试流 Run 内多步 HTTP 共享会话。
 * <p>
 * HTTP 节点 {@code useRunSession=true} 或 Script {@code ctx.session} 时，
 * 经 {@link RunSessionSupport} 在转发前注入 Cookie、响应后吸收 Set-Cookie。
 * 当前按 cookie 名称全局存储，不区分 domain/path。
 */
@Getter
public class FlowRunSession {

    private final Map<String, String> cookies = new LinkedHashMap<>();

    public boolean isEmpty() {
        return cookies.isEmpty();
    }

    /** 合并响应 Set-Cookie 到 jar */
    public void absorbSetCookieHeaders(Map<String, String> responseHeaders) {
        if (responseHeaders == null || responseHeaders.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
            if (entry.getKey() == null || !"set-cookie".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            absorbSetCookieValue(entry.getValue());
        }
    }

    /** 组装请求 Cookie 头；无 cookie 时返回空串 */
    public String buildCookieHeader() {
        if (cookies.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        cookies.forEach((name, value) -> {
            if (name != null && !name.isBlank()) {
                parts.add(name + "=" + (value != null ? value : ""));
            }
        });
        return String.join("; ", parts);
    }

    private void absorbSetCookieValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String firstSegment = raw.split(";")[0].trim();
        int eq = firstSegment.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String name = firstSegment.substring(0, eq).trim();
        String value = firstSegment.substring(eq + 1).trim();
        if (!name.isEmpty()) {
            cookies.put(name, value);
        }
    }

    /** 供步骤报告摘要 */
    public Map<String, String> snapshot() {
        return Map.copyOf(cookies);
    }
}
