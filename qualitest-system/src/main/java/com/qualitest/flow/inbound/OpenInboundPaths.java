package com.qualitest.flow.inbound;

import java.util.List;
import java.util.Locale;

/**
 * 开放入站 path 规范化与保留前缀校验。
 */
public final class OpenInboundPaths {

    /** 不得登记为开放入站的管理面 / 平台前缀 */
    private static final List<String> RESERVED_PREFIXES = List.of(
            "/login",
            "/logout",
            "/register",
            "/captchaImage",
            "/system",
            "/monitor",
            "/tool",
            "/common",
            "/profile",
            "/swagger",
            "/v3/api-docs",
            "/druid",
            "/mcp",
            "/open/inbound"
    );

    private OpenInboundPaths() {
    }

    /**
     * 规范化：trim、小写、保证以 {@code /} 开头、去掉末尾多余 {@code /}（根 {@code /} 除外）。
     * 空或不合法返回空串。
     */
    public static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim();
        if (p.isEmpty()) {
            return "";
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.toLowerCase(Locale.ROOT);
    }

    /** 是否落在保留前缀上（不可作为开放入站 path） */
    public static boolean isReserved(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            return true;
        }
        for (String prefix : RESERVED_PREFIXES) {
            if (normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
