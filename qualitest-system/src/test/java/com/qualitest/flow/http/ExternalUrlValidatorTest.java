package com.qualitest.flow.http;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 ExternalUrlValidator：外联 URL 协议与 host 校验（不做白名单拦截）。
 * 边界：纯函数，无网络。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ExternalUrlValidatorTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalUrlValidatorTest {

    /**
     * 前提：http/https 且含合法 host（含 127.0.0.1、任意域名）。
     * 期望：均不抛 FlowExecutionException。
     */
    @Test
    @Order(1)
    @DisplayName("校验：允许 http/https 合法 URL")
    void allowsHttpHttpsUrl() {
        assertDoesNotThrow(() -> ExternalUrlValidator.validate("https://oauth.example.com/token"));
        assertDoesNotThrow(() -> ExternalUrlValidator.validate("http://127.0.0.1/admin"));
        assertDoesNotThrow(() -> ExternalUrlValidator.validate("https://evil.com/token"));
    }

    /**
     * 前提：URL 仅空白。
     * 期望：抛 TF_HTTP_EXTERNAL_DENIED。
     */
    @Test
    @Order(2)
    @DisplayName("校验：空白 URL 拒绝")
    void rejectsBlankUrl() {
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> ExternalUrlValidator.validate("  "));
        assertEquals(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, ex.getErrorCode());
    }

    /**
     * 前提：协议为 ftp。
     * 期望：抛 TF_HTTP_EXTERNAL_DENIED。
     */
    @Test
    @Order(3)
    @DisplayName("校验：非 http(s) 协议拒绝")
    void rejectsInvalidScheme() {
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> ExternalUrlValidator.validate("ftp://example.com/file"));
        assertEquals(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, ex.getErrorCode());
    }

    /**
     * 前提：URL 缺 host（https:///path）。
     * 期望：抛 TF_HTTP_EXTERNAL_DENIED。
     */
    @Test
    @Order(4)
    @DisplayName("校验：缺少 host 时拒绝")
    void rejectsMissingHost() {
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> ExternalUrlValidator.validate("https:///path"));
        assertEquals(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, ex.getErrorCode());
    }
}
