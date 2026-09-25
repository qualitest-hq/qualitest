package com.qualitest.api.util;

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
 * 测 ExpectedResponseKindSupport：期望形态规范化与实际对照。
 * 边界：纯函数，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ExpectedResponseKindSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExpectedResponseKindSupportTest {

    /**
     * 前提：空白或未知字符串。
     * 期望：归一为 json。
     */
    @Test
    @Order(1)
    @DisplayName("normalize：空白与未知 → json")
    void normalize_defaultsToJson() {
        assertEquals("json", ExpectedResponseKindSupport.normalize(null));
        assertEquals("json", ExpectedResponseKindSupport.normalize(""));
        assertEquals("json", ExpectedResponseKindSupport.normalize("xml"));
        assertEquals("html", ExpectedResponseKindSupport.normalize("HTML"));
        assertEquals("any", ExpectedResponseKindSupport.normalize("any"));
    }

    /**
     * 前提：解析后的 Map/List 与 HTML 字符串。
     * 期望：detectActualKind 分别为 json / nonJson。
     */
    @Test
    @Order(2)
    @DisplayName("detectActualKind：JSON 对象 vs HTML 字符串")
    void detectActualKind_jsonVsHtml() {
        assertEquals("json", ExpectedResponseKindSupport.detectActualKind(Map.of("code", 200)));
        assertEquals("json", ExpectedResponseKindSupport.detectActualKind(List.of(1, 2)));
        assertEquals("nonJson", ExpectedResponseKindSupport.detectActualKind("<html>登录</html>"));
        assertEquals("nonJson", ExpectedResponseKindSupport.detectActualKind(null));
    }

    /**
     * 前提：期望 json，实际为 HTML 字符串。
     * 期望：matches 为 false（悠度假假绿场景）。
     */
    @Test
    @Order(3)
    @DisplayName("matches：期望 json 遇到 HTML → false")
    void matches_jsonExpected_htmlActual_false() {
        assertFalse(ExpectedResponseKindSupport.matches(
                "json", ExpectedResponseKindSupport.detectActualKind("<html>未登录</html>")));
        assertTrue(ExpectedResponseKindSupport.matches(
                "json", ExpectedResponseKindSupport.detectActualKind(Map.of("ok", true))));
        assertTrue(ExpectedResponseKindSupport.matches("any", "nonJson"));
    }

    /**
     * 前提：responseConfig 缺字段或显式 html。
     * 期望：缺省 json；显式 html 可读出。
     */
    @Test
    @Order(4)
    @DisplayName("fromResponseConfigJson：缺省与显式")
    void fromResponseConfigJson_defaultAndExplicit() {
        assertEquals("json", ExpectedResponseKindSupport.fromResponseConfigJson(null));
        assertEquals("json", ExpectedResponseKindSupport.fromResponseConfigJson(
                "{\"configVersion\":1,\"responses\":[]}"));
        assertEquals("html", ExpectedResponseKindSupport.fromResponseConfigJson(
                "{\"expectedResponseKind\":\"html\",\"responses\":[]}"));
    }

    /**
     * 前提：空白配置。
     * 期望：matchesOrSkip 恒 true。
     */
    @Test
    @Order(5)
    @DisplayName("matchesOrSkip：无接口配置跳过")
    void matchesOrSkip_nullConfig_true() {
        assertTrue(ExpectedResponseKindSupport.matchesOrSkip(null, "<html/>"));
        assertTrue(ExpectedResponseKindSupport.matchesOrSkip("", "<html/>"));
        assertTrue(ExpectedResponseKindSupport.matchesOrSkip("   ", "<html/>"));
    }
}
