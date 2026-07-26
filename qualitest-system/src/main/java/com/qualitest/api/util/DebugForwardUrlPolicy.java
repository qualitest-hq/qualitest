package com.qualitest.api.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 调试转发目标 URL 基础校验（暂不做 IP / 主机白名单）。
 */
public final class DebugForwardUrlPolicy {

    private DebugForwardUrlPolicy() {
    }

    /**
     * @return null 表示通过；非 null 为拒绝原因
     */
    public static String validateTargetUrl(String url) {
        if (url == null || url.isBlank()) {
            return "目标 URL 不能为空";
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return "目标 URL 格式无效";
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return "目标 URL 缺少协议";
        }
        String s = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(s) && !"https".equals(s)) {
            return "仅允许 http 或 https 协议";
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "目标 URL 缺少主机名";
        }
        return null;
    }
}
