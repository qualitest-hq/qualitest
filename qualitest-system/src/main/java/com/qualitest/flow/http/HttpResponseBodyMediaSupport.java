package com.qualitest.flow.http;

import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.util.MediaContentTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从调试转发结果构造 Run 步骤 {@code http.response.bodyMedia}（不落媒体本体）。
 */
public final class HttpResponseBodyMediaSupport {

    private static final Pattern BINARY_HINT = Pattern.compile(
            "\\[binary\\s+([^\\s]+)\\s+·\\s+(\\d+)\\s+bytes]",
            Pattern.CASE_INSENSITIVE);

    private HttpResponseBodyMediaSupport() {
    }

    /**
     * 媒体响应且未落库时返回元数据；文本/JSON 返回 null。
     */
    public static Map<String, Object> buildMarker(DebugHttpForwardResult forwardResult) {
        if (forwardResult == null) {
            return null;
        }
        if (!DebugHttpForwardResult.BODY_ENCODING_BASE64.equals(forwardResult.getBodyEncoding())) {
            return null;
        }
        String bodyText = forwardResult.getBodyText() != null ? forwardResult.getBodyText() : "";
        String mime = MediaContentTypes.contentTypeFromHeaders(forwardResult.getResponseHeaders());
        Long bytes = null;
        Matcher m = BINARY_HINT.matcher(bodyText);
        if (m.find()) {
            if (mime == null || mime.isBlank()) {
                mime = m.group(1);
            }
            try {
                bytes = Long.parseLong(m.group(2));
            } catch (NumberFormatException ignored) {
                bytes = null;
            }
        }
        if (mime == null || mime.isBlank()) {
            mime = "application/octet-stream";
        }
        String kind = MediaContentTypes.kindFromMime(mime);
        if (kind == null) {
            return null;
        }
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("kind", kind);
        marker.put("mime", mime);
        if (bytes != null) {
            marker.put("bytes", bytes);
        }
        marker.put("stored", false);
        marker.put("truncated", bodyText.contains("响应体已截断"));
        return marker;
    }
}
