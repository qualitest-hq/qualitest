package com.qualitest.plugin.manifest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测说明书 ai.tools 解析。
 */
class PluginAiToolsSupportTest {

    @Test
    @DisplayName("解析合法 tools；非法名与未知 capability 跳过")
    void listTools_filtersInvalid() {
        PluginManifest manifest = PluginManifest.builder()
                .id("qualitest.sut-sys-clock-dial")
                .capabilities(List.of(
                        PluginManifest.Capability.builder().name("clock.advance").effect("write").build()))
                .ai(Map.of("tools", List.of(
                        Map.of(
                                "name", "sut_clock_advance",
                                "description", "拨快",
                                "capability", "clock.advance",
                                "parameters", Map.of("type", "object")),
                        Map.of(
                                "name", "bad-name",
                                "capability", "clock.advance"),
                        Map.of(
                                "name", "unknown_cap",
                                "capability", "clock.missing"))))
                .build();
        List<PluginAiTool> tools = PluginAiToolsSupport.listTools(manifest);
        assertEquals(1, tools.size());
        assertEquals("sut_clock_advance", tools.get(0).getName());
        assertEquals("clock.advance", tools.get(0).getCapability());
        assertTrue(tools.get(0).hasParameters());
    }
}
