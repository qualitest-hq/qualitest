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

    private boolean forwarded;
    private Integer status;
    private String statusText;
    private Map<String, String> responseHeaders;
    private String bodyText;
    private String error;
    private String errorCode;

    public static DebugHttpForwardResult policyError(String message) {
        return DebugHttpForwardResult.builder()
                .forwarded(false)
                .status(null)
                .statusText("")
                .responseHeaders(Map.of())
                .bodyText("")
                .error(message)
                .errorCode("POLICY")
                .build();
    }

    public static DebugHttpForwardResult success(int status, String statusText,
                                                 Map<String, String> headers, String bodyText) {
        return DebugHttpForwardResult.builder()
                .forwarded(true)
                .status(status)
                .statusText(statusText != null ? statusText : "")
                .responseHeaders(headers != null ? headers : Map.of())
                .bodyText(bodyText != null ? bodyText : "")
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
                .error(message)
                .errorCode(errorCode != null ? errorCode : "UNKNOWN")
                .build();
    }
}
