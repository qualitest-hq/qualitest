package com.qualitest.api.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * HTTP 出站目标白名单。
 * <p>
 * 用于限制调试转发、测试流 HTTP、脚本 HTTP、快照还原等出站请求可访问的目标。
 * 配置项：{@code qualitest.http.egress.enabled}、{@code qualitest.http.egress.allowlist}。
 * <ul>
 *   <li>关闭时：只校验 URL 为 http/https 且含主机名</li>
 *   <li>开启时：再要求目标规范化后的 {@code host:port} 落在白名单内（可写域名、IPv4、IPv6、本机别名）</li>
 * </ul>
 */
@Component
public class HttpEgressAllowlist {

    /** 是否启用 host:port 白名单拦截 */
    private final boolean enabled;

    /** 已规范化的允许目标集合，元素形如 {@code host:port} 或 {@code [ipv6]:port} */
    private final Set<String> allowlist;

    /**
     * @param enabled      是否启用白名单；false 时只做协议与主机校验
     * @param allowlistCsv 逗号分隔的允许目标，每项为 {@code host:port}
     */
    public HttpEgressAllowlist(
            @Value("${qualitest.http.egress.enabled:false}") boolean enabled,
            @Value("${qualitest.http.egress.allowlist:}") String allowlistCsv) {
        this.enabled = enabled;
        this.allowlist = parseAllowlist(allowlistCsv);
    }

    /**
     * 校验 URL 协议为 http/https，且含非空主机名；不检查白名单。
     *
     * @param url 待校验的完整 URL
     * @return 通过返回 null；失败返回中文拒绝原因
     */
    public static String validateSchemeAndHost(String url) {
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

    /**
     * 出站前完整校验：先做协议与主机检查；白名单开启时再核对 host:port。
     *
     * @param url 待访问的完整 URL（可含路径与查询串）
     * @return 通过返回 null；失败返回中文拒绝原因
     */
    public String check(String url) {
        String basic = validateSchemeAndHost(url);
        if (basic != null) {
            return basic;
        }
        if (!enabled) {
            return null;
        }
        URI uri = URI.create(url.trim());
        String key = toHostPortKey(uri);
        if (key == null || !allowlist.contains(key)) {
            String host = uri.getHost();
            return "目标不在 HTTP 出口白名单内: " + (key != null ? key : host);
        }
        return null;
    }

    /**
     * 将配置字符串解析为不可变的白名单集合。
     *
     * @param csv 逗号分隔的 {@code host:port} 列表；空则得到空集合
     * @return 规范化后的允许目标集合
     */
    static Set<String> parseAllowlist(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String raw : csv.split(",")) {
            if (raw == null) {
                continue;
            }
            String entry = normalizeAllowlistEntry(raw);
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            out.add(entry);
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * 从 URI 生成白名单比对键：小写 host + 有效端口；IPv6 写成 {@code [addr]:port}。
     *
     * @param uri 已解析的目标 URI
     * @return {@code host:port}；主机缺失时返回 null
     */
    static String toHostPortKey(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        return formatHostPort(host, resolvePort(uri));
    }

    /**
     * 解析有效端口：URL 写了端口则用该端口；未写时 http 视为 80、https 视为 443。
     *
     * @param uri 目标 URI
     * @return 用于白名单比对的端口号
     */
    static int resolvePort(URI uri) {
        int port = uri.getPort();
        if (port >= 0) {
            return port;
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "http";
        return "https".equals(scheme) ? 443 : 80;
    }

    /**
     * 将主机名与端口拼成白名单键；含冒号的地址按 IPv6 加方括号。
     *
     * @param host 主机名或 IP（可带或不带方括号）
     * @param port 端口号
     * @return 形如 {@code example.com:443} 或 {@code [::1]:8801}
     */
    static String formatHostPort(String host, int port) {
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]") && h.length() > 2) {
            h = h.substring(1, h.length() - 1);
        }
        String hostPart = h.contains(":") ? "[" + h + "]" : h;
        return hostPart + ":" + port;
    }

    /**
     * 规范化配置里的单条白名单：去空白、小写，并统一成 {@code host:port} / {@code [ipv6]:port}。
     *
     * @param entry 原始配置项
     * @return 规范化键；空串返回 null；无法解析端口时原样返回小写串
     */
    static String normalizeAllowlistEntry(String entry) {
        String e = entry.trim().toLowerCase(Locale.ROOT);
        if (e.isEmpty()) {
            return null;
        }
        // IPv6：[addr]:port
        if (e.startsWith("[")) {
            int close = e.indexOf(']');
            if (close > 1 && close + 1 < e.length() && e.charAt(close + 1) == ':') {
                String addr = e.substring(1, close);
                String portPart = e.substring(close + 2);
                try {
                    return formatHostPort(addr, Integer.parseInt(portPart));
                } catch (NumberFormatException ignored) {
                    return e;
                }
            }
            return e;
        }
        int colon = e.lastIndexOf(':');
        if (colon <= 0 || colon == e.length() - 1) {
            return e;
        }
        String hostPart = e.substring(0, colon);
        String portPart = e.substring(colon + 1);
        try {
            return formatHostPort(hostPart, Integer.parseInt(portPart));
        } catch (NumberFormatException ignored) {
            return e;
        }
    }
}
