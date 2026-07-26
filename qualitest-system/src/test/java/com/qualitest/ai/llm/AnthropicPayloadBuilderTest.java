package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AnthropicPayloadBuilder} 单元测试：验证 Anthropic Claude API 请求/响应格式的转换。
 * <p>
 * 被测对象负责：将内部 {@link LlmMessage} 列表转为 Anthropic messages 格式（含 system 块、
 * assistant tool_use、user tool_result）；构建请求体（structured output、thinking、caching、stream）；
 * 解析响应中的 text、thinking、tool_use 与 usage（含 cache token）。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=AnthropicPayloadBuilderTest
 */
class AnthropicPayloadBuilderTest {

    /**
     * {@link AnthropicPayloadBuilder#toAnthropicMessages}：system 消息提取到 systemBlockList；
     * assistant 含 tool_use 块；连续 tool_result 合并为一条 user 消息；is_error 标记错误结果。
     */
    @Test
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
     * {@link AnthropicPayloadBuilder#toAnthropicToolChoice}：指定工具名时返回 type=tool + name。
     */
    @Test
    void toAnthropicToolChoice_supportsNamedTool() {
        JSONObject choice = AnthropicPayloadBuilder.toAnthropicToolChoice("search_apis");
        assertEquals("tool", choice.getString("type"));
        assertEquals("search_apis", choice.getString("name"));
    }

    /**
     * {@link AnthropicPayloadBuilder#buildBody}：request 含 responseFormat/thinking/promptCaching/stream 时
     * 应写入 output_format、thinking.budget_tokens、system 数组及 beta headers。
     */
    @Test
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
     * {@link AnthropicPayloadBuilder#parseResponse}：从 Anthropic 响应 JSON 中提取
     * text 内容、thinking 内容、tool_use 列表、finishReason 及 usage（含 cache token）。
     */
    @Test
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
