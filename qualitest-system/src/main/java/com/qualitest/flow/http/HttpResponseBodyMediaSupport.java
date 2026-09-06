package com.qualitest.flow.http;

import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.util.MediaContentTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为测试流 Run 步骤构造 {@code http.response.bodyMedia} 元数据。
 * <p>
 * 仅当响应为裸媒体（{@code bodyEncoding=base64}）时写出标识，
 * 不把媒体字节写入步骤详情。JSON 内嵌 base64（如验证码 {@code img} 字段）不生成此对象。
 * <p>
 * 字段含义：
 * <ul>
 *   <li>{@code kind} — image / video / audio / pdf</li>
 *   <li>{@code mime} — 如 image/png</li>
 *   <li>{@code bytes} — 原始字节数（能解析时）</li>
 *   <li>{@code stored} — 固定 false，表示未落库媒体本体</li>
 *   <li>{@code truncated} — 响应是否被截断</li>
 * </ul>
 */
public final class HttpResponseBodyMediaSupport {

    /** 匹配 bodyText 中的 {@code [binary image/png · 1234 bytes]} 提示 */
    private static final Pattern BINARY_HINT = Pattern.compile(
            "\\[binary\\s+([^\\s]+)\\s+·\\s+(\\d+)\\s+bytes]",
            Pattern.CASE_INSENSITIVE);

    private HttpResponseBodyMediaSupport() {
    }

    /**
     * 从转发结果生成 bodyMedia；非裸媒体返回 null。
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
