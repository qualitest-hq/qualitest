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
 * {@link RunSessionSupport} 单元测试：useRunSession 解析与转发前 Cookie 注入。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=RunSessionSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunSessionSupportTest {

    /**
     * 节点 data.useRunSession 应支持 boolean 与字符串 "true"。
     * 期望：null/空 map 为 false；true/"true" 为 true。
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
     * applyToForward 应将 session 中的 Cookie 合并进转发参数，覆盖同名 Cookie 头。
     * 期望：headers 仅一条 Cookie；值含 session 吸收的 sid。
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
