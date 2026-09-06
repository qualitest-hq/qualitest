package com.qualitest.api.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * API 调试 HTTP 转发的响应结果。
 * <p>
 * 文本/JSON 响应只填 {@code bodyText}；图、音视频、PDF 等裸二进制响应额外填
 * {@code bodyEncoding=base64} 与 {@code bodyBase64}，{@code bodyText} 改为短提示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebugHttpForwardResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 响应体按 UTF-8 文本处理 */
    public static final String BODY_ENCODING_TEXT = "text";
    /** 响应体为媒体二进制，以 Base64 给出 */
    public static final String BODY_ENCODING_BASE64 = "base64";

    /** 是否已向目标 URL 发起转发（策略拒绝时为 false） */
    private boolean forwarded;
    /** 目标 HTTP 状态码 */
    private Integer status;
    /** 状态短语 */
    private String statusText;
    /** 目标响应头（已去掉 hop-by-hop） */
    private Map<String, String> responseHeaders;
    /**
     * 文本预览：JSON/纯文本为原文；媒体二进制为
     * {@code [binary image/png · N bytes]} 一类提示，截断时追加说明。
     */
    private String bodyText;
    /**
     * 响应体编码方式：{@code text} 或 {@code base64}。
     * 媒体 Content-Type（image/video/audio/application/pdf）时为 base64。
     */
    private String bodyEncoding;
    /**
     * 媒体响应体的 Base64（可能已按上限截断）；文本路径为 null。
     * 供前端拼 data URL 做预览。
     */
    private String bodyBase64;
    /** 转发失败或策略错误信息；成功为 null */
    private String error;
    /** 错误分类码，如 POLICY、TIMEOUT */
    private String errorCode;

    /** 策略拒绝：未真正转发 */
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

    /** 成功响应（纯文本 / JSON） */
    public static DebugHttpForwardResult success(int status, String statusText,
                                                 Map<String, String> headers, String bodyText) {
        return success(status, statusText, headers, bodyText, BODY_ENCODING_TEXT, null);
    }

    /**
     * 成功响应。
     *
     * @param bodyEncoding text 或 base64
     * @param bodyBase64   媒体字节的 Base64；文本路径传 null
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

    /** 已转发但目标侧或网络出错 */
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
