package com.qualitest.flow.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 StatusCheckResolver：2xx / whitelist / off 门禁。
 * 边界：缺省、空白名单退回 2xx。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=StatusCheckResolverTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StatusCheckResolverTest {

    /** 前提：未配置 statusCheck。期望：按 2xx 门禁。 */
    @Test
    @Order(1)
    @DisplayName("缺省按 2xx")
    void resolve_default_twoXx() {
        StatusCheckResolver.Resolved r = StatusCheckResolver.resolve(Map.of());
        assertEquals(StatusCheckResolver.MODE_2XX, r.getMode());
        assertTrue(r.passes(200));
        assertFalse(r.passes(401));
    }

    /** 前提：whitelist [200,401]。期望：200/401 过，500 不过。 */
    @Test
    @Order(2)
    @DisplayName("whitelist 仅放行列表内状态码")
    void resolve_whitelist() {
        Map<String, Object> data = Map.of(
                "statusCheck", Map.of("mode", "whitelist", "values", List.of(200, 401)));
        StatusCheckResolver.Resolved r = StatusCheckResolver.resolve(data);
        assertEquals(StatusCheckResolver.MODE_WHITELIST, r.getMode());
        assertTrue(r.passes(200));
        assertTrue(r.passes(401));
        assertFalse(r.passes(500));
    }

    /** 前提：mode=off。期望：任意状态码通过。 */
    @Test
    @Order(3)
    @DisplayName("off 放行任意状态码")
    void resolve_off() {
        Map<String, Object> data = Map.of("statusCheck", Map.of("mode", "off"));
        StatusCheckResolver.Resolved r = StatusCheckResolver.resolve(data);
        assertTrue(r.passes(401));
        assertTrue(r.passes(500));
    }

    /** 前提：whitelist 但 values 为空。期望：退回 2xx。 */
    @Test
    @Order(4)
    @DisplayName("空白名单退回 2xx")
    void resolve_emptyWhitelist_fallsBackTo2xx() {
        Map<String, Object> data = Map.of(
                "statusCheck", Map.of("mode", "whitelist", "values", List.of()));
        StatusCheckResolver.Resolved r = StatusCheckResolver.resolve(data);
        assertEquals(StatusCheckResolver.MODE_2XX, r.getMode());
        assertFalse(r.passes(401));
    }
}
