package com.qualitest.flow.http;

import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static com.qualitest.flow.support.FlowTestSections.quote;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 ExternalUrlValidator：外联 URL 协议与 host 校验（不做白名单拦截）。
 * 边界：纯函数；存量 begin/end 保留。
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
    void allowsHttpHttpsUrl() {
        begin("allowsHttpHttpsUrl");
        assertDoesNotThrow(() -> ExternalUrlValidator.validate("https://oauth.example.com/token"));
        assertDoesNotThrow(() -> ExternalUrlValidator.validate("http://127.0.0.1/admin"));
        assertDoesNotThrow(() -> ExternalUrlValidator.validate("https://evil.com/token"));
        log("schemes=http,https accepted");
        end("allowsHttpHttpsUrl");
    }

    /**
     * 前提：URL 仅空白。
     * 期望：抛 TF_HTTP_EXTERNAL_DENIED。
     */
    @Test
    @Order(2)
    void rejectsBlankUrl() {
        begin("rejectsBlankUrl");
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> ExternalUrlValidator.validate("  "));
        assertEquals(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, ex.getErrorCode());
        log("errorCode=" + ex.getErrorCode().getCode());
        end("rejectsBlankUrl");
    }

    /**
     * 前提：协议为 ftp。
     * 期望：抛 TF_HTTP_EXTERNAL_DENIED。
     */
    @Test
    @Order(3)
    void rejectsInvalidScheme() {
        begin("rejectsInvalidScheme");
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> ExternalUrlValidator.validate("ftp://example.com/file"));
        assertEquals(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, ex.getErrorCode());
        log("url=" + quote("ftp://example.com/file") + " rejected=true");
        end("rejectsInvalidScheme");
    }

    /**
     * 前提：URL 缺 host（https:///path）。
     * 期望：抛 TF_HTTP_EXTERNAL_DENIED。
     */
    @Test
    @Order(4)
    void rejectsMissingHost() {
        begin("rejectsMissingHost");
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> ExternalUrlValidator.validate("https:///path"));
        assertEquals(FlowErrorCode.TF_HTTP_EXTERNAL_DENIED, ex.getErrorCode());
        log("errorCode=" + ex.getErrorCode().getCode());
        end("rejectsMissingHost");
    }
}
