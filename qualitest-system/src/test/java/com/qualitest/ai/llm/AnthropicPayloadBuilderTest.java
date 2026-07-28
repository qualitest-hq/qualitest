package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 AnthropicPayloadBuilder：内部 LlmMessage ↔ Anthropic 请求/响应格式转换。
 * 边界：纯函数，不发 HTTP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AnthropicPayloadBuilderTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnthropicPayloadBuilderTest {

    /**
     * 前提：消息列表含 system、user、assistant tool_use 与连续 tool_result（含 is_error）。
     * 期望：system 提取到 systemBlockList；messages 含 assistant tool_use 与合并的 user tool_result。
     */
    @Test
    @Order(1)
    @DisplayName("消息转换为 Anthropic system/tool_use/tool_result")
    void toAnthropicMessages_convertsSystemToolAndAssistantToolUse() {
        java.util.ArrayList<JSONObject> systemBlockList = new java.util.ArrayList<>();
        JSONArray messages = AnthropicPayloadBuilder.toAnthropicMessages(List.of(
                LlmMessage.system("sys prompt"),
                LlmMessage.user("hello"),
                LlmMessage.assistant(null, List.of(LlmToolCall.builder()
                        .id("toolu_1")
                        .name("search_apis")
                        .argumentsJson("{\"keyword\":\"login\"}")
                        .build())),
                LlmMessage.tool("toolu_1", "{\"items\":[]}", false),
                LlmMessage.tool("toolu_2", "{\"error\":\"failed\"}", true)
        ), systemBlockList);

        assertEquals(1, systemBlockList.size());
        assertEquals("sys prompt", systemBlockList.get(0).getString("text"));
        assertEquals(3, messages.size());

        JSONObject assistant = messages.getJSONObject(1);
        assertEquals("assistant", assistant.getString("role"));
        JSONArray assistantContent = assistant.getJSONArray("content");
        assertEquals("tool_use", assistantContent.getJSONObject(0).getString("type"));

        JSONObject toolResults = messages.getJSONObject(2);
        assertEquals("user", toolResults.getString("role"));
        JSONArray toolResultBlocks = toolResults.getJSONArray("content");
        assertEquals(2, toolResultBlocks.size());
        assertTrue(toolResultBlocks.getJSONObject(1).getBooleanValue("is_error"));
    }

    /**
     * 前提：指定工具名 search_apis。
     * 期望：toAnthropicToolChoice 返回 type=tool、name=search_apis。
     */
    @Test
    @Order(2)
    @DisplayName("指定工具名生成 tool choice")
    void toAnthropicToolChoice_supportsNamedTool() {
        JSONObject choice = AnthropicPayloadBuilder.toAnthropicToolChoice("search_apis");
        assertEquals("tool", choice.getString("type"));
        assertEquals("search_apis", choice.getString("name"));
    }

    /**
     * 前提：request 启用 responseFormat、thinking、promptCaching、stream。
     * 期望：body 含 output_format/thinking/system 数组，stream=true，beta headers 非空。
     */
    @Test
    @Order(3)
    @DisplayName("构建 body 含结构化输出与 thinking")
    void buildBody_appliesStructuredOutputThinkingAndCaching() {
        LlmModelConfig cfg = LlmModelConfig.builder()
                .modelName("claude-test")
                .maxTokens(1024)
                .build();
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.system("system"), LlmMessage.user("go")))
                .responseFormat(Map.of("type", "json_object"))
                .promptCaching(true)
                .extendedThinking(true)
                .thinkingBudgetTokens(4096)
                .stream(true)
                .build();

        JSONObject body = AnthropicPayloadBuilder.buildBody(cfg, request);
        assertNotNull(body.get("output_format"));
        assertNotNull(body.get("thinking"));
        assertEquals(4096, body.getJSONObject("thinking").getInteger("budget_tokens"));
        assertTrue(body.getBooleanValue("stream"));
        assertTrue(body.get("system") instanceof JSONArray);
        assertFalse(AnthropicPayloadBuilder.resolveBetaHeaders(request).isEmpty());
    }

    /**
     * 前提：Anthropic 响应 JSON 含 thinking/text/tool_use 与 cache token usage。
     * 期望：parseResponse 提取 content、thinkingContent、toolCalls、finishReason 及 usage。
     */
    @Test
    @Order(4)
    @DisplayName("解析响应提取 text/thinking/tool_use/usage")
    void parseResponse_readsTextThinkingToolUseAndUsage() {
        String responseBody = """
                {
                  "stop_reason": "tool_use",
                  "content": [
                    {"type": "thinking", "thinking": "plan"},
                    {"type": "text", "text": "answer"},
                    {"type": "tool_use", "id": "toolu_1", "name": "search_apis", "input": {"keyword": "login"}}
                  ],
                  "usage": {
                    "input_tokens": 100,
                    "output_tokens": 20,
                    "cache_read_input_tokens": 50,
                    "cache_creation_input_tokens": 10
                  }
                }
                """;
        LlmChatResponse parsed = AnthropicPayloadBuilder.parseResponse(responseBody);
        assertEquals("answer", parsed.getContent());
        assertEquals("plan", parsed.getThinkingContent());
        assertEquals(1, parsed.getToolCalls().size());
        assertEquals("tool_use", parsed.getFinishReason());
        assertEquals(100, parsed.getUsage().getInputTokens());
        assertEquals(50, parsed.getUsage().getCacheReadTokens());
    }
}
