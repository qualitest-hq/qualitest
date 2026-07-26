package com.qualitest.flow.http;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 外联 HTTP URL 基础校验（占位符解析后的完整 URL）。
 * <p>
 * 要求：scheme 为 http/https，且含非空主机名。不做项目级域名白名单、内网/metadata 拦截。
 */
public final class ExternalUrlValidator {

    private ExternalUrlValidator() {
    }

    /**
     * 校验解析后的完整 URL 是否可用于出站 HTTP。
     *
     * @param resolvedUrl 占位符已解析的完整 URL
     */
    public static void validate(String resolvedUrl) {
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, "外联 URL 为空");
        }
        URI uri;
        try {
            uri = new URI(resolvedUrl.trim());
        } catch (URISyntaxException e) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, "外联 URL 格式无效");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, "外联 URL 须为 http/https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new FlowExecutionException(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, "外联 URL 缺少主机名");
        }
    }
}
