package com.qualitest.flow.session;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FlowRunSession} 单元测试：Set-Cookie 吸收与 Cookie 请求头拼装。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=FlowRunSessionTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowRunSessionTest {

    /**
     * 吸收响应 Set-Cookie 后应能拼出 Cookie 请求头。
     * 期望：session 非空；buildCookieHeader 含 sid 值。
     */
    @Test
    @Order(1)
    void absorbAndBuildCookieHeader() {
        begin("absorbAndBuildCookieHeader");
        FlowRunSession session = new FlowRunSession();
        session.absorbSetCookieHeaders(Map.of("Set-Cookie", "sid=abc123; Path=/; HttpOnly"));

        assertFalse(session.isEmpty());
        String cookieHeader = session.buildCookieHeader();
        assertTrue(cookieHeader.contains("sid=abc123"));
        log("cookieHeader=" + cookieHeader);
        end("absorbAndBuildCookieHeader");
    }
}
