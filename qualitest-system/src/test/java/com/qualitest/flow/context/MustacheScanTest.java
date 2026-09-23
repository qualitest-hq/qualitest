package com.qualitest.flow.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测谁：MustacheScan 占位符扫描与替换。
 * 边界：合法路径、假 {{ 混排、反斜杠原样、空段、简写标识符。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=MustacheScanTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MustacheScanTest {

    /**
     * 前提：模板含两个合法路径占位与字面量。
     * 期望：listInners 保序返回 trim 后路径。
     */
    @Test
    @Order(1)
    @DisplayName("listInners 保序收集合法路径")
    void listInners_ordered() {
        assertEquals(
                List.of("flow.a", "asset.x.y"),
                MustacheScan.listInners("Bearer {{flow.a}} / {{ asset.x.y }}")
        );
    }

    /**
     * 前提：空 {{}} 与正常占位。
     * 期望：空段不当占位；后续合法路径仍替换。
     */
    @Test
    @Order(2)
    @DisplayName("空 {{}} 跳过；replace 替换合法路径")
    void replace_skipsEmptyAndSubstitutes() {
        assertEquals("X", MustacheScan.replace("{{flow.t}}", inner -> "X"));
        assertEquals("{{}}ok", MustacheScan.replace("{{}}{{flow.t}}", inner -> "ok"));
        assertEquals(List.of("flow.t"), MustacheScan.listInners("{{}}{{flow.t}}"));
    }

    /**
     * 前提：未闭合 {{。
     * 期望：原文保留。
     */
    @Test
    @Order(3)
    @DisplayName("未闭合定界符保留原文")
    void unclosed_preserved() {
        AtomicInteger n = new AtomicInteger();
        MustacheScan.forEach("pre {{flow.x", span -> n.incrementAndGet());
        assertEquals(0, n.get());
        assertEquals("pre {{flow.x", MustacheScan.replace("pre {{flow.x", inner -> "Y"));
    }

    /**
     * 前提：说明性 {{…}} 夹着真占位。
     * 期望：假开括号不吞掉 {{flow.x}}。
     */
    @Test
    @Order(4)
    @DisplayName("字面 {{ 混排时仍命中真占位")
    void literalBraces_doNotSwallowRealPlaceholder() {
        String raw = "请写 {{ 再填 {{flow.x}} 结束";
        assertEquals(List.of("flow.x"), MustacheScan.listInners(raw));
        assertEquals("请写 {{ 再填 OK 结束", MustacheScan.replace(raw, inner -> "OK"));
        assertTrue(MustacheScan.isPlaceholderPath("flow.token"));
        assertFalse(MustacheScan.isPlaceholderPath("token"));
        assertFalse(MustacheScan.isPlaceholderPath("flow."));
    }

    /**
     * 前提：简写与带点路径。
     * 期望：仅单段标识符为 short；默认求值不认 short。
     */
    @Test
    @Order(5)
    @DisplayName("isShortIdentifier；默认不把 short 当占位")
    void isShortIdentifier_andDefaultScan() {
        assertTrue(MustacheScan.isShortIdentifier("token"));
        assertTrue(MustacheScan.isShortIdentifier(" _x1 "));
        assertFalse(MustacheScan.isShortIdentifier("flow.token"));
        assertEquals(List.of(), MustacheScan.listInners("{{token}}"));
        assertEquals(
                List.of("token"),
                MustacheScan.listInnersWhere("{{token}}", MustacheScan::isShortIdentifier)
        );
    }

    /**
     * 前提：替换值含 $；原文在占位前自带反斜杠。
     * 期望：$ 原样；反斜杠不剥不解释，仅随字面区留下（占位仍按路径求值）。
     */
    @Test
    @Order(6)
    @DisplayName("replace 保留 $；自带反斜杠不剥除")
    void replace_dollarAndKeepBackslash() {
        assertEquals("price $1.00", MustacheScan.replace("price {{flow.p}}", inner -> "$1.00"));
        assertEquals(
                "show \\NO end",
                MustacheScan.replace("show \\{{flow.x}} end", inner -> "NO")
        );
    }
}
