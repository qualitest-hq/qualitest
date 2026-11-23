package com.qualitest.plugin.manifest;

import com.qualitest.plugin.PluginException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 PluginManifestParser：JSONC 与字段校验。
 * 边界：纯解析，不访问库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=PluginManifestParserTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PluginManifestParserTest {

    private final PluginManifestParser parser = new PluginManifestParser();

    /**
     * 前提：合法 JSONC 含行注释。
     * 期望：解析出 id/version/capabilities。
     */
    @Test
    @Order(1)
    @DisplayName("解析含注释的说明书")
    void parse_jsoncWithComments() {
        String jsonc = """
                {
                  // sample
                  "id": "qualitest.sut-db-archive-point",
                  "version": "0.1.0",
                  "displayName": "存档点",
                  "capabilities": [
                    { "name": "archive.create", "effect": "write", "gate": "none" }
                  ],
                  "hooks": [
                    { "name": "onCheckpoint", "order": 100, "actions": ["archive.create"] }
                  ],
                  "runtime": {
                    "kind": "ops-agent",
                    "agent": { "bundle": "embedded", "cli": { "bin": "sdap", "pingArgs": ["ping"] } }
                  }
                }
                """;
        PluginManifest m = parser.parse(jsonc);
        assertEquals("qualitest.sut-db-archive-point", m.getId());
        assertEquals("0.1.0", m.getVersion());
        assertEquals(1, m.getCapabilities().size());
        assertEquals("ops-agent", m.getRuntime().getKind());
    }

    /**
     * 前提：hook action 不在 capabilities。
     * 期望：抛错。
     */
    @Test
    @Order(2)
    @DisplayName("hook 必须引用本包能力")
    void parse_rejectsForeignHookAction() {
        String json = """
                {
                  "id": "qualitest.x",
                  "version": "1.0.0",
                  "displayName": "x",
                  "capabilities": [{ "name": "a", "effect": "read" }],
                  "hooks": [{ "name": "onCheckpoint", "actions": ["b"] }],
                  "runtime": {
                    "kind": "ops-agent",
                    "agent": { "cli": { "bin": "x", "pingArgs": ["ping"] } }
                  }
                }
                """;
        PluginException ex = assertThrows(PluginException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().contains("hook action"));
    }
}
