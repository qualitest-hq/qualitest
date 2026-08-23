package com.qualitest.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * example 补全：只在缺失时按 schema 生成，已有样例不改。
 */
class ApiConfigExampleEnricherTest {

    @Test
    @DisplayName("已有 example 不覆盖")
    void enrichRequestKeepsExistingExample() {
        String raw = """
                {"configVersion":1,"method":"POST","body":{"mode":"json","json":{
                  "schema":{"type":"object","properties":{"a":{"type":"string"}}},
                  "example":{"a":"keep-me"}
                }}}
                """;
        String out = ApiConfigExampleEnricher.enrichRequestConfig(raw);
        assertTrue(out.contains("keep-me"));
        assertFalse(out.contains("\"a\":\"\"") && out.contains("keep-me") == false);
    }

    @Test
    @DisplayName("缺失 example 时按 schema 生成")
    void enrichRequestFillsMissingExample() {
        String raw = """
                {"configVersion":1,"method":"POST","body":{"mode":"json","json":{
                  "schema":{"type":"object","properties":{"username":{"type":"string"}}}
                }}}
                """;
        String out = ApiConfigExampleEnricher.enrichRequestConfig(raw);
        assertTrue(out.contains("\"example\""));
        assertTrue(out.contains("username"));
    }

    @Test
    @DisplayName("响应已有 example 不覆盖")
    void enrichResponseKeepsExisting() {
        String raw = """
                {"configVersion":1,"responses":[{"id":"r1","contentType":"json",
                  "schema":{"type":"object","properties":{"code":{"type":"integer"}}},
                  "example":{"code":42}}]}
                """;
        String out = ApiConfigExampleEnricher.enrichResponseConfig(raw);
        assertTrue(out.contains("42"));
    }
}
