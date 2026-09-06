package com.qualitest.api.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * API 调试 HTTP 转发响应（与前端 debugTransport 约定对齐）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebugHttpForwardResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文本编码常量 */
    public static final String BODY_ENCODING_TEXT = "text";
    /** Base64 编码常量（媒体二进制） */
    public static final String BODY_ENCODING_BASE64 = "base64";

    private boolean forwarded;
    private Integer status;
    private String statusText;
    private Map<String, String> responseHeaders;
    private String bodyText;
    /** text | base64；媒体响应为 base64 */
    private String bodyEncoding;
    /** bodyEncoding=base64 时的响应体切片 */
    private String bodyBase64;
    private String error;
    private String errorCode;

    public static DebugHttpForwardResult policyError(String message) {
        return DebugHttpForwardResult.builder()
                .forwarded(false)
                .status(null)
                .statusText("")
                .responseHeaders(Map.of())
                .bodyText("")
                .bodyEncoding(BODY_ENCODING_TEXT)
                .bodyBase64(null)
                .error(message)
                .errorCode("POLICY")
                .build();
    }

    public static DebugHttpForwardResult success(int status, String statusText,
                                                 Map<String, String> headers, String bodyText) {
        return success(status, statusText, headers, bodyText, BODY_ENCODING_TEXT, null);
    }

    /**
     * @param bodyEncoding text 或 base64
     * @param bodyBase64   媒体响应的 Base64；文本路径传 null
     */
    public static DebugHttpForwardResult success(int status, String statusText,
                                                 Map<String, String> headers, String bodyText,
                                                 String bodyEncoding, String bodyBase64) {
        return DebugHttpForwardResult.builder()
                .forwarded(true)
                .status(status)
                .statusText(statusText != null ? statusText : "")
                .responseHeaders(headers != null ? headers : Map.of())
                .bodyText(bodyText != null ? bodyText : "")
                .bodyEncoding(bodyEncoding != null ? bodyEncoding : BODY_ENCODING_TEXT)
                .bodyBase64(bodyBase64)
                .error(null)
                .errorCode(null)
                .build();
    }

    public static DebugHttpForwardResult forwardError(String message, String errorCode, Integer status) {
        return DebugHttpForwardResult.builder()
                .forwarded(true)
                .status(status)
                .statusText("")
                .responseHeaders(Map.of())
                .bodyText("")
                .bodyEncoding(BODY_ENCODING_TEXT)
                .bodyBase64(null)
                .error(message)
                .errorCode(errorCode != null ? errorCode : "UNKNOWN")
                .build();
    }
}
