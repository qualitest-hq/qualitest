package com.qualitest.ai.llm.discovery;

import com.qualitest.ai.llm.LlmClientException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * 模型发现 URL 工具：规范化 baseUrl、拼接各协议发现端点，并拦截 SSRF 内网访问。
 */
public final class LlmDiscoveryUrlUtils {

    /** 常见内网/回环主机名前缀，用于快速拒绝 */
    private static final Pattern PRIVATE_HOST = Pattern.compile(
            "^(127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.|localhost$)",
            Pattern.CASE_INSENSITIVE);

    private LlmDiscoveryUrlUtils() {
    }

    /**
     * 去除首尾空白与末尾斜杠；空值抛错。
     */
    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LlmClientException("接口地址未配置");
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * 拼接 OpenAI 兼容协议的 GET /v1/models 地址。
     */
    public static String buildOpenAiModelsUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/v1")) {
            return normalized + "/models";
        }
        return normalized + "/v1/models";
    }

    /**
     * 拼接 Anthropic GET /v1/models 地址。
     */
    public static String buildAnthropicModelsUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/v1")) {
            return normalized + "/models";
        }
        return normalized + "/v1/models";
    }

    /**
     * 拼接 Ollama GET /api/tags 地址；自动剥离 baseUrl 末尾的 /v1 后缀。
     */
    public static String buildOllamaTagsUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
        }
        return normalized + "/api/tags";
    }

    /**
     * 校验 baseUrl 协议与主机：默认禁止内网/回环地址；
     * allowPrivateBaseUrl=true 时跳过主机检查（供本地 Ollama）。
     */
    public static void validateBaseUrl(String baseUrl, boolean allowPrivateBaseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LlmClientException("接口地址未配置");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new LlmClientException("接口地址格式错误");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new LlmClientException("仅支持 http/https 协议");
        }
        if (allowPrivateBaseUrl) {
            return;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new LlmClientException("接口地址无效");
        }
        if (PRIVATE_HOST.matcher(host).find() || "localhost".equalsIgnoreCase(host)) {
            throw new LlmClientException("不允许访问内网地址，如需本地 Ollama 请开启 ai.llm.allow-private-base-url");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()) {
                    throw new LlmClientException("不允许访问内网地址，如需本地 Ollama 请开启 ai.llm.allow-private-base-url");
                }
            }
        } catch (UnknownHostException ex) {
            throw new LlmClientException("无法解析接口地址：" + host);
        }
    }
}
