package com.qualitest.api.service.impl;

import com.qualitest.api.params.DebugHttpForwardParams.DebugBodySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 urlencoded 请求体字符串组装：从 fields 编码、raw 优先、空输入。
 * 边界：无网络。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=DebugHttpForwardUrlencodedTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DebugHttpForwardUrlencodedTest {

    /**
     * 前提：kind=urlencoded，仅填 fields（phone/code），无 raw。
     * 期望：编码为 phone=…&code=…，不能是空串。
     */
    @Test
    @Order(1)
    @DisplayName("仅 fields 时编码为 name=value")
    void resolveUrlencodedRaw_fromFields() {
        DebugBodySpec spec = new DebugBodySpec();
        spec.setKind("urlencoded");
        spec.setFields(List.of(
                List.of("phone", "13800000001"),
                List.of("code", "123456")));

        String raw = DebugHttpForwardServiceImpl.resolveUrlencodedRaw(spec);

        assertTrue(raw.contains("phone=13800000001"));
        assertTrue(raw.contains("code=123456"));
        assertTrue(raw.contains("&"));
    }

    /**
     * 前提：同时有 raw 与 fields。
     * 期望：优先 raw。
     */
    @Test
    @Order(2)
    @DisplayName("已有 raw 时优先用 raw")
    void resolveUrlencodedRaw_prefersRaw() {
        DebugBodySpec spec = new DebugBodySpec();
        spec.setKind("urlencoded");
        spec.setRaw("a=1");
        spec.setFields(List.of(List.of("phone", "13800000001")));

        assertEquals("a=1", DebugHttpForwardServiceImpl.resolveUrlencodedRaw(spec));
    }

    /**
     * 前提：fields/raw 皆空。
     * 期望：空串。
     */
    @Test
    @Order(3)
    @DisplayName("无 fields 无 raw 为空串")
    void resolveUrlencodedRaw_empty() {
        DebugBodySpec spec = new DebugBodySpec();
        spec.setKind("urlencoded");

        assertEquals("", DebugHttpForwardServiceImpl.resolveUrlencodedRaw(spec));
    }
}
