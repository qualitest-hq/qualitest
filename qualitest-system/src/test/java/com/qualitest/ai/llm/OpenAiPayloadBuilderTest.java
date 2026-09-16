package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 Chat Completions 请求体里思考开关的组包结果。
 * 边界：纯函数转换，不发 HTTP。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=OpenAiPayloadBuilderTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenAiPayloadBuilderTest {

    /**
     * 前提：思考风格为 deepseek_thinking，本轮关闭思考。
     * 期望：请求体含 thinking.type=disabled，且没有 reasoning_effort。
     */
    @Test
    @Order(1)
    @DisplayName("DeepSeek 关思考时发 thinking.disabled")
    void buildBody_deepseekThinkingDisabled() {
        LlmModelConfig cfg = LlmModelConfig.builder()
                .modelName("deepseek-v4-flash")
                .maxTokens(1024)
                .thinkingControl(ThinkingControlStyles.DEEPSEEK_THINKING)
                .build();
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hi")))
                .reasoningEnabled(false)
                .build();

        JSONObject body = OpenAiPayloadBuilder.buildBody(cfg, request);

        assertEquals("disabled", body.getJSONObject("thinking").getString("type"));
        assertFalse(body.containsKey("reasoning_effort"));
    }

    /**
     * 前提：思考风格为 deepseek_thinking，本轮开启思考。
     * 期望：thinking.type=enabled，且带 reasoning_effort=medium。
     */
    @Test
    @Order(2)
    @DisplayName("DeepSeek 开思考时发 thinking.enabled 与 effort")
    void buildBody_deepseekThinkingEnabled() {
        LlmModelConfig cfg = LlmModelConfig.builder()
                .modelName("deepseek-v4-pro")
                .maxTokens(1024)
                .thinkingControl(ThinkingControlStyles.DEEPSEEK_THINKING)
                .build();
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hi")))
                .reasoningEnabled(true)
                .build();

        JSONObject body = OpenAiPayloadBuilder.buildBody(cfg, request);

        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
        assertEquals("medium", body.getString("reasoning_effort"));
    }

    /**
     * 前提：openai_reasoning_effort 风格且开启思考。
     * 期望：仅 reasoning_effort，无 thinking 对象。
     */
    @Test
    @Order(3)
    @DisplayName("OpenAI effort 开思考只发 reasoning_effort")
    void buildBody_openaiEffortEnabled() {
        LlmModelConfig cfg = LlmModelConfig.builder()
                .modelName("o3-mini")
                .maxTokens(1024)
                .thinkingControl(ThinkingControlStyles.OPENAI_REASONING_EFFORT)
                .build();
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hi")))
                .reasoningEnabled(true)
                .build();

        JSONObject body = OpenAiPayloadBuilder.buildBody(cfg, request);

        assertEquals("medium", body.getString("reasoning_effort"));
        assertFalse(body.containsKey("thinking"));
    }

    /**
     * 前提：无 thinkingControl，且关闭思考。
     * 期望：不写入 thinking / reasoning_effort。
     */
    @Test
    @Order(4)
    @DisplayName("无思考风格时不发思考字段")
    void buildBody_noThinkingControlOmitsFields() {
        LlmModelConfig cfg = LlmModelConfig.builder()
                .modelName("gpt-4o")
                .maxTokens(1024)
                .build();
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hi")))
                .reasoningEnabled(false)
                .build();

        JSONObject body = OpenAiPayloadBuilder.buildBody(cfg, request);

        assertFalse(body.containsKey("thinking"));
        assertFalse(body.containsKey("reasoning_effort"));
    }

    /**
     * 前提：openai_reasoning_effort 风格且关闭思考。
     * 期望：不发 reasoning_effort。
     */
    @Test
    @Order(5)
    @DisplayName("OpenAI effort 关思考不发字段")
    void buildBody_openaiEffortDisabledOmits() {
        LlmModelConfig cfg = LlmModelConfig.builder()
                .modelName("o3-mini")
                .maxTokens(1024)
                .thinkingControl(ThinkingControlStyles.OPENAI_REASONING_EFFORT)
                .build();
        LlmChatRequest request = LlmChatRequest.builder()
                .messages(List.of(LlmMessage.user("hi")))
                .reasoningEnabled(false)
                .build();

        JSONObject body = OpenAiPayloadBuilder.buildBody(cfg, request);

        assertFalse(body.containsKey("thinking"));
        assertFalse(body.containsKey("reasoning_effort"));
    }
}
