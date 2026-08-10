package com.qualitest.flow.session;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析响应 {@code Set-Cookie}：取 name=value 第一段，忽略 Path/HttpOnly 等属性。
 * <p>
 * 供 extracts {@code from=setCookie} 解析响应头。
 */
public final class SetCookieParser {

    private SetCookieParser() {
    }

    /**
     * 从响应头 Map 按 Cookie 名取值（名大小写敏感，与常见浏览器/Jar 一致）。
     * 头名 {@code Set-Cookie} 大小写不敏感；同名多次出现时后写覆盖。
     */
    public static String findCookieValue(Map<String, ?> responseHeaders, String cookieName) {
        if (responseHeaders == null || responseHeaders.isEmpty()
                || cookieName == null || cookieName.isBlank()) {
            return null;
        }
        Map<String, String> parsed = parseAll(responseHeaders);
        return parsed.get(cookieName);
    }

    /**
     * 解析响应头中全部 Set-Cookie 为 name → value。
     */
    public static Map<String, String> parseAll(Map<String, ?> responseHeaders) {
        Map<String, String> out = new LinkedHashMap<>();
        if (responseHeaders == null || responseHeaders.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, ?> entry : responseHeaders.entrySet()) {
            if (entry.getKey() == null || !"set-cookie".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            Object raw = entry.getValue();
            if (raw == null) {
                continue;
            }
            if (raw instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    absorbOne(out, item != null ? String.valueOf(item) : null);
                }
            } else {
                // 部分客户端会把多条 Set-Cookie 拼成逗号分隔；按 "; " 属性段切分不可靠，
                // 这里按常见单值处理；多值场景优先走 Iterable。
                absorbOne(out, String.valueOf(raw));
            }
        }
        return out;
    }

    /** 单条 Set-Cookie 原始值 → 写入 name/value */
    static void absorbOne(Map<String, String> target, String raw) {
        if (target == null || raw == null || raw.isBlank()) {
            return;
        }
        String firstSegment = raw.split(";", 2)[0].trim();
        int eq = firstSegment.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String name = firstSegment.substring(0, eq).trim();
        String value = firstSegment.substring(eq + 1).trim();
        if (!name.isEmpty()) {
            target.put(name, value);
        }
    }
}
