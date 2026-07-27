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
 * 测 FlowRunSession：吸收 Set-Cookie 并拼装 Cookie 请求头。
 * 边界：单条 Set-Cookie。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowRunSessionTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowRunSessionTest {

    /**
     * 前提：吸收 Set-Cookie: sid=abc123。
     * 期望：session 非空；buildCookieHeader 含 sid=abc123。
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
