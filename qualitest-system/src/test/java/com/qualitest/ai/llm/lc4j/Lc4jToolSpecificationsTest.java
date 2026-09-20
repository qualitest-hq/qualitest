package com.qualitest.ai.llm.lc4j;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具规格与 baseUrl 规范化单测。
 * 覆盖：OpenAI 风格 function Map 转工具规格；厂商根地址补齐 /v1/。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Lc4jToolSpecificationsTest {

    /**
     * 标准 function 工具含 name、description、parameters.properties。
     * 转换后名称与必填字段应正确。
     */
    @Test
    @Order(1)
    @DisplayName("OpenAI function Map 转为 ToolSpecification")
    void fromOpenAiMap_convertsFunctionTool() {
        Map<String, Object> tool = Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "search_apis",
                        "description", "搜索接口",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of("type", "string", "description", "关键词")),
                                "required", List.of("keyword"))));

        var spec = Lc4jToolSpecifications.fromOpenAiMap(tool);
        assertNotNull(spec);
        assertEquals("search_apis", spec.name());
        assertEquals("搜索接口", spec.description());
        assertNotNull(spec.parameters());
        assertTrue(spec.parameters().properties().containsKey("keyword"));
        assertEquals(List.of("keyword"), spec.parameters().required());
    }

    /**
     * 根地址无 /v1 时应补齐；已以 /v1 结尾时只规范尾斜杠。
     */
    @Test
    @Order(2)
    @DisplayName("baseUrl 规范化补齐 /v1/")
    void normalizeOpenAiBaseUrl_appendsV1() {
        assertEquals("https://api.deepseek.com/v1/",
                Lc4jClientFactory.normalizeOpenAiBaseUrl("https://api.deepseek.com"));
        assertEquals("https://api.openai.com/v1/",
                Lc4jClientFactory.normalizeOpenAiBaseUrl("https://api.openai.com/v1"));
    }
}
