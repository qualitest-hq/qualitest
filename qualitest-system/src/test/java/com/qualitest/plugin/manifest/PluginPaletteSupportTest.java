package com.qualitest.plugin.manifest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测说明书 palette.nodes 解析：优先 argsSchema，无则回退 configSchema。
 */
class PluginPaletteSupportTest {

    /**
     * 前提：两项分别写 argsSchema 与 configSchema。
     * 期望：都能解析出非空 schema；按 id 能查到第二项。
     */
    @Test
    @DisplayName("解析 argsSchema 与 configSchema 别名")
    void listNodes_prefersArgsSchema_fallsBackConfigSchema() {
        PluginManifest manifest = PluginManifest.builder()
                .id("qualitest.demo")
                .ui(Map.of(
                        "palette", Map.of(
                                "nodes", List.of(
                                        Map.of(
                                                "id", "a1",
                                                "label", "A",
                                                "capability", "cap.a",
                                                "argsSchema", Map.of("type", "object")
                                        ),
                                        Map.of(
                                                "id", "b1",
                                                "label", "B",
                                                "capability", "cap.b",
                                                "configSchema", Map.of(
                                                        "type", "object",
                                                        "required", List.of("x")
                                                )
                                        )
                                )
                        )
                ))
                .build();

        List<PluginPaletteNode> nodes = PluginPaletteSupport.listNodes(manifest);
        assertEquals(2, nodes.size());
        assertTrue(nodes.get(0).hasArgsSchema());
        assertEquals("object", nodes.get(0).getArgsSchema().get("type"));
        assertTrue(nodes.get(1).hasArgsSchema());
        assertEquals(List.of("x"), nodes.get(1).getArgsSchema().get("required"));

        PluginPaletteNode found = PluginPaletteSupport.findByContributionId(manifest, "b1");
        assertNotNull(found);
        assertEquals("cap.b", found.getCapability());
    }

    /**
     * 前提：说明书无 palette。
     * 期望：列表为空；按 id 查找为 null。
     */
    @Test
    @DisplayName("无 palette 返回空")
    void listNodes_emptyWithoutPalette() {
        assertTrue(PluginPaletteSupport.listNodes(PluginManifest.builder().id("x").build()).isEmpty());
        assertFalse(PluginPaletteSupport.findByContributionId(
                PluginManifest.builder().id("x").build(), "a") != null);
    }
}
