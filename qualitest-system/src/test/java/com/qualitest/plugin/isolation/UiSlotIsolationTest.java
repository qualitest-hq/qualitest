package com.qualitest.plugin.isolation;

import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import com.qualitest.plugin.manifest.PluginManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 UiSlotIsolation：同槽异 id 拒、同 id 放过、异槽放过。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=UiSlotIsolationTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UiSlotIsolationTest {

    private static final String SLOT_A = "ui-slot:flow.badge.a";
    private static final String SLOT_B = "ui-slot:flow.badge.b";

    @Test
    @Order(1)
    @DisplayName("同槽异 pluginId 拒启用")
    void requireFree_sameSlotDifferentPlugin_throws() {
        PluginException ex = assertThrows(PluginException.class, () ->
                UiSlotIsolation.requireFree(
                        "qualitest.p2",
                        List.of(SLOT_A),
                        Map.of("qualitest.p1", List.of(SLOT_A))));
        assertEquals(PluginErrorCode.ISOLATION_MUTEX, ex.getErrorCode());
        assertTrue(ex.getMessage().contains(SLOT_A));
        assertTrue(ex.getMessage().contains("qualitest.p1"));
    }

    @Test
    @Order(2)
    @DisplayName("同 pluginId 重开不冲突")
    void requireFree_samePluginId_ok() {
        assertDoesNotThrow(() ->
                UiSlotIsolation.requireFree(
                        "qualitest.p1",
                        List.of(SLOT_A),
                        Map.of("qualitest.p1", List.of(SLOT_A))));
    }

    @Test
    @Order(3)
    @DisplayName("不同槽可同开")
    void requireFree_differentSlots_ok() {
        assertDoesNotThrow(() ->
                UiSlotIsolation.requireFree(
                        "qualitest.p2",
                        List.of(SLOT_B),
                        Map.of("qualitest.p1", List.of(SLOT_A))));
    }

    @Test
    @Order(4)
    @DisplayName("非 ui-slot isolation 不进入槽列表")
    void slotsOf_ignoresNonSlotTags() {
        PluginManifest m = PluginManifest.builder()
                .id("qualitest.x")
                .version("0.1.0")
                .displayName("x")
                .isolation(List.of("other-tag", SLOT_A, "ui-slot:"))
                .build();
        assertEquals(List.of(SLOT_A), UiSlotIsolation.slotsOf(m));
    }
}
