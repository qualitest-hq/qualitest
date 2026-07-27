package com.qualitest.flow.session;

import com.qualitest.api.params.DebugHttpForwardParams;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 RunSessionSupport：useRunSession 解析与转发前 Cookie 注入。
 * 边界：boolean/字符串 true；覆盖同名 Cookie 头。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=RunSessionSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunSessionSupportTest {

    /**
     * 前提：data 为 null/空 map/true/"true"。
     * 期望：前两者 false；true 与 "true" 为 true。
     */
    @Test
    @Order(1)
    void isUseRunSession_parsesBoolean() {
        begin("isUseRunSession_parsesBoolean");
        assertFalse(RunSessionSupport.isUseRunSession(null));
        assertFalse(RunSessionSupport.isUseRunSession(Map.of()));
        assertTrue(RunSessionSupport.isUseRunSession(Map.of("useRunSession", true)));
        assertTrue(RunSessionSupport.isUseRunSession(Map.of("useRunSession", "true")));
        log("null=false bool=true strTrue=true");
        end("isUseRunSession_parsesBoolean");
    }

    /**
     * 前提：session 已吸收 sid=xyz；params 原有 Cookie: old=1。
     * 期望：headers 仅一条 Cookie，值含 sid=xyz。
     */
    @Test
    @Order(2)
    void applyToForward_injectsCookieHeader() {
        begin("applyToForward_injectsCookieHeader");
        FlowRunSession session = new FlowRunSession();
        session.absorbSetCookieHeaders(Map.of("Set-Cookie", "sid=xyz; Path=/"));

        DebugHttpForwardParams params = new DebugHttpForwardParams();
        params.setHeaders(List.of(new DebugHttpForwardParams.HeaderPair("Cookie", "old=1")));

        RunSessionSupport.applyToForward(params, session);

        assertNotNull(params.getHeaders());
        assertEquals(1, params.getHeaders().size());
        assertEquals("Cookie", params.getHeaders().get(0).getName());
        assertTrue(params.getHeaders().get(0).getValue().contains("sid=xyz"));
        log("cookie=" + params.getHeaders().get(0).getValue());
        end("applyToForward_injectsCookieHeader");
    }
}
