package com.qualitest.api.util;

import java.util.Locale;
import java.util.Map;

/**
 * HTTP 响应媒体 Content-Type 识别（调试转发与 Run bodyMedia 共用）。
 */
public final class MediaContentTypes {

    private MediaContentTypes() {
    }

    /**
     * @return 规范化 MIME（无参数）；非可预览媒体返回 null
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
     * @return image / video / audio / pdf；无法识别返回 null
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

    /** 从响应头取 Content-Type 主类型 */
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
