package com.qualitest.flow.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：{@link SetCookieParser}。
 * 边界：属性段、大小写头名、缺名、Iterable 多值；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=SetCookieParserTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SetCookieParserTest {

    /**
     * 前提：Set-Cookie 带 Path/HttpOnly。
     * 期望：只取 name=value，忽略属性。
     */
    @Test
    @Order(1)
    @DisplayName("解析：忽略 Path/HttpOnly 属性段")
    void findCookieValue_stripsAttributes() {
        Map<String, String> headers = Map.of("Set-Cookie", "sid=abc; Path=/; HttpOnly");
        assertEquals("abc", SetCookieParser.findCookieValue(headers, "sid"));
    }

    /**
     * 前提：头名小写；查找不存在的 Cookie。
     * 期望：命中大小写不敏感；缺名返回 null。
     */
    @Test
    @Order(2)
    @DisplayName("解析：头名大小写不敏感；缺名 null")
    void findCookieValue_headerCase_andMissing() {
        Map<String, String> headers = Map.of("set-cookie", "JSESSIONID=x1");
        assertEquals("x1", SetCookieParser.findCookieValue(headers, "JSESSIONID"));
        assertNull(SetCookieParser.findCookieValue(headers, "other"));
        assertNull(SetCookieParser.findCookieValue(headers, null));
        assertTrue(SetCookieParser.parseAll(Map.of()).isEmpty());
    }

    /**
     * 前提：Set-Cookie 值为字符串列表。
     * 期望：逐条吸收。
     */
    @Test
    @Order(3)
    @DisplayName("解析：Iterable 多条 Set-Cookie")
    void parseAll_iterableValues() {
        Map<String, Object> headers = Map.of("Set-Cookie", List.of("a=1; Path=/", "b=2"));
        Map<String, String> parsed = SetCookieParser.parseAll(headers);
        assertEquals("1", parsed.get("a"));
        assertEquals("2", parsed.get("b"));
    }
}
