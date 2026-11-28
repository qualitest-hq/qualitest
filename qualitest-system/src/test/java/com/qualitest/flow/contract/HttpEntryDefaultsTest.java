package com.qualitest.flow.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 HttpEntryDefaults：完整 fallbackOut 样例与形状判据。
 * 边界：子流业务对象不算完整 HTTP 形。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpEntryDefaultsTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpEntryDefaultsTest {

    /**
     * 前提：默认 HTTP fallbackOut 与子流业务兜底。
     * 期望：前者完整形为真，后者为假。
     */
    @Test
    @Order(1)
    @DisplayName("完整形只认 HTTP fallbackOut")
    void isCompleteFallbackOut() {
        assertTrue(HttpEntryDefaults.isCompleteFallbackOut(HttpEntryDefaults.defaultFallbackOut()));
        assertFalse(HttpEntryDefaults.isCompleteFallbackOut(SubflowEntryDefaults.defaultFallbackOut()));
    }

    /**
     * 前提：缺 status / headers / body 的残缺 Map。
     * 期望：各报一条形状错误。
     */
    @Test
    @Order(2)
    @DisplayName("形状错误文案齐全")
    void appendFallbackOutShapeErrors() {
        List<String> errors = new ArrayList<>();
        HttpEntryDefaults.appendFallbackOutShapeErrors("e1", Map.of(), errors);
        assertEquals(3, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.contains("status")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("headers")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("body")));
    }
}
