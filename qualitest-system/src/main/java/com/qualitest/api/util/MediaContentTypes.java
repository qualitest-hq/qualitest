package com.qualitest.api.util;

import java.util.Locale;
import java.util.Map;

/**
 * 判断 HTTP 响应 Content-Type 是否为可预览媒体，并解析 MIME / 媒体种类。
 * <p>
 * 支持：{@code image/*}、{@code video/*}、{@code audio/*}、{@code application/pdf}。
 */
public final class MediaContentTypes {

    private MediaContentTypes() {
    }

    /**
     * 从 Content-Type 取可预览媒体的主 MIME（去掉 charset 等参数）。
     *
     * @return 如 {@code image/png}；非媒体或空则 null
     */
    public static String mediaMime(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String mime = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/")
                || mime.startsWith("video/")
                || mime.startsWith("audio/")
                || "application/pdf".equals(mime)) {
            return mime;
        }
        return null;
    }

    /**
     * MIME → 媒体种类字符串。
     *
     * @return {@code image} / {@code video} / {@code audio} / {@code pdf}；无法识别则 null
     */
    public static String kindFromMime(String mime) {
        String m = mime != null ? mime.toLowerCase(Locale.ROOT) : "";
        if (m.startsWith("image/")) {
            return "image";
        }
        if (m.startsWith("video/")) {
            return "video";
        }
        if (m.startsWith("audio/")) {
            return "audio";
        }
        if ("application/pdf".equals(m)) {
            return "pdf";
        }
        return null;
    }

    /**
     * 从响应头 Map 读取 Content-Type 主类型（小写、无参数）。
     *
     * @return 如 {@code image/png}；没有则 null
     */
    public static String contentTypeFromHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && "content-type".equalsIgnoreCase(e.getKey()) && e.getValue() != null) {
                return e.getValue().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }
}
