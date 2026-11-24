package com.qualitest.flow.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 EntryInputShapeValidator：开放入站 inputShape 运行时校验。
 * 边界：空 shape、body/query/headers 必填缺失、类型合法通过。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=EntryInputShapeValidatorTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EntryInputShapeValidatorTest {

    /**
     * 前提：inputShape 为 null 或 Map 为空。
     * 期望：不校验，返回 empty。
     */
    @Test
    @Order(1)
    @DisplayName("空 shape 跳过校验")
    void emptyShape_ok() {
        assertTrue(EntryInputShapeValidator.validate(null, Map.of(), Map.of(), Map.of()).isEmpty());
        assertTrue(EntryInputShapeValidator.validate(Map.of(), Map.of(), Map.of(), Map.of()).isEmpty());
    }

    /**
     * 前提：三节皆无字段声明。
     * 期望：不校验，返回 empty。
     */
    @Test
    @Order(2)
    @DisplayName("三节无字段声明时跳过校验")
    void shapeSectionsEmpty_ok() {
        Map<String, Object> shape = Map.of("body", List.of(), "query", List.of(), "headers", List.of());
        assertTrue(EntryInputShapeValidator.validate(shape, Map.of(), Map.of(), Map.of()).isEmpty());
    }

    /**
     * 前提：body 声明必填 name，请求体不含该键。
     * 期望：返回 body 缺少必填字段文案。
     */
    @Test
    @Order(3)
    @DisplayName("body 必填缺失返回错误")
    void bodyRequiredMissing_error() {
        Map<String, Object> shape = Map.of(
                "body", List.of(Map.of("name", "userId", "type", "text", "required", true))
        );
        Optional<String> err = EntryInputShapeValidator.validate(
                shape, Map.of(), Map.of(), Map.of("other", "x"));
        assertTrue(err.isPresent());
        assertEquals("body 缺少必填字段: userId", err.get());
    }

    /**
     * 前提：query 声明必填 orderNo，查询串未带。
     * 期望：返回 query 缺少必填字段文案。
     */
    @Test
    @Order(4)
    @DisplayName("query 必填缺失返回错误")
    void queryRequiredMissing_error() {
        Map<String, Object> shape = Map.of(
                "query", List.of(Map.of("name", "orderNo", "type", "text", "required", true))
        );
        Optional<String> err = EntryInputShapeValidator.validate(
                shape, Map.of("page", "1"), Map.of(), null);
        assertTrue(err.isPresent());
        assertEquals("query 缺少必填字段: orderNo", err.get());
    }

    /**
     * 前提：headers 声明必填 X-Trace-Id，请求头不含（大小写不敏感匹配）。
     * 期望：返回 headers 缺少必填字段文案。
     */
    @Test
    @Order(5)
    @DisplayName("headers 必填缺失返回错误")
    void headerRequiredMissing_error() {
        Map<String, Object> shape = Map.of(
                "headers", List.of(Map.of("name", "X-Trace-Id", "type", "text", "required", true))
        );
        Optional<String> err = EntryInputShapeValidator.validate(
                shape, Map.of(), Map.of("Content-Type", "application/json"), Map.of());
        assertTrue(err.isPresent());
        assertEquals("headers 缺少必填字段: X-Trace-Id", err.get());
    }

    /**
     * 前提：三节必填字段均已提供。
     * 期望：校验通过，返回 empty。
     */
    @Test
    @Order(6)
    @DisplayName("必填齐全时通过")
    void allRequiredPresent_ok() {
        Map<String, Object> shape = Map.of(
                "body", List.of(Map.of("name", "userId", "type", "text", "required", true)),
                "query", List.of(Map.of("name", "orderNo", "type", "text", "required", true)),
                "headers", List.of(Map.of("name", "X-Trace-Id", "type", "text", "required", true))
        );
        Optional<String> err = EntryInputShapeValidator.validate(
                shape,
                Map.of("orderNo", "A001"),
                Map.of("x-trace-id", "t-1"),
                Map.of("userId", "u1"));
        assertTrue(err.isEmpty());
    }
}