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
 * {@link ExternalUrlValidator} 单元测试：外联 URL 协议与 host 校验（不做白名单拦截）。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ExternalUrlValidatorTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalUrlValidatorTest {

    /**
     * http/https 且含合法 host 的 URL 应通过校验。
     * 期望：不抛出 {@link FlowExecutionException}。
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
     * 空白 URL 应拒绝。
     * 期望：{@link FlowErrorCode#TF_HTTP_EXTERNAL_DENIED}。
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
     * 非 http/https 协议应拒绝。
     * 期望：{@link FlowErrorCode#TF_HTTP_EXTERNAL_DENIED}。
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
     * 缺少 host 的 URL 应拒绝。
     * 期望：{@link FlowErrorCode#TF_HTTP_EXTERNAL_DENIED}。
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
