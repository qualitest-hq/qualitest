package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 请求体构建与响应解析。
 * <p>
 * 纯 JSON 转换逻辑，不依赖 HTTP 客户端，便于单元测试。
 * 负责将 {@link LlmMessage}、{@link LlmChatRequest} 转为 Anthropic 请求格式，
 * 并将响应中的 text / thinking / tool_use 块统一解析为 {@link LlmChatResponse}。
 */
public final class AnthropicPayloadBuilder {

    /** Structured Outputs 能力所需的 anthropic-beta 标识 */
    public static final String BETA_STRUCTURED_OUTPUTS = "structured-outputs-2025-11-13";
    /** Prompt Caching 能力所需的 anthropic-beta 标识 */
    public static final String BETA_PROMPT_CACHING = "prompt-caching-2024-07-31";
    /** Extended Thinking 能力所需的 anthropic-beta 标识 */
    public static final String BETA_EXTENDED_THINKING = "extended-thinking-2024-05-24";

    private AnthropicPayloadBuilder() {
    }

    /**
     * 构建 POST /v1/messages 请求体。
     * 依次填充 model、max_tokens、system、messages、tools、output_format、thinking、stream 等字段。
     */
    public static JSONObject buildBody(LlmModelConfig cfg, LlmChatRequest request) {
        List<JSONObject> systemBlocks = new ArrayList<>();
        JSONArray messages = toAnthropicMessages(request.getMessages(), systemBlocks);
        JSONObject body = new JSONObject();
        body.put("model", cfg.getModelName());
        body.put("max_tokens", cfg.getMaxTokens());
        applySystem(body, systemBlocks, request.isPromptCaching());
        body.put("messages", messages);
        applyTools(body, request);
        applyResponseFormat(body, request.getResponseFormat());
        applyThinking(body, request);
        if (request.isStream()) {
            body.put("stream", true);
        }
        return body;
    }

    /**
     * 根据请求特性计算需附加的 anthropic-beta 请求头。
     * 启用 Structured Outputs、Prompt Caching 或 Extended Thinking 时返回对应 beta 标识列表。
     */
    public static List<String> resolveBetaHeaders(LlmChatRequest request) {
        List<String> betas = new ArrayList<>();
        if (usesStructuredOutput(request.getResponseFormat())) {
            betas.add(BETA_STRUCTURED_OUTPUTS);
        }
        if (request.isPromptCaching()) {
            betas.add(BETA_PROMPT_CACHING);
        }
        if (request.isExtendedThinking()) {
            betas.add(BETA_EXTENDED_THINKING);
        }
        return betas;
    }

    /** 解析非流式 JSON 响应，提取 text、thinking、tool_use 与 usage */
    public static LlmChatResponse parseResponse(String responseBody) {
        JSONObject root = JSON.parseObject(responseBody);
        JSONArray content = root.getJSONArray("content");
        String stopReason = root.getString("stop_reason");
        StringBuilder textBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        List<LlmToolCall> toolCalls = new ArrayList<>();
        appendContentBlocks(content, textBuilder, thinkingBuilder, toolCalls);
        return LlmChatResponse.builder()
                .content(textBuilder.length() > 0 ? textBuilder.toString() : null)
                .thinkingContent(thinkingBuilder.length() > 0 ? thinkingBuilder.toString() : null)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .finishReason(stopReason)
                .usage(parseUsage(root.getJSONObject("usage")))
                .build();
    }

    /**
     * 解析单条 SSE 流式事件，增量更新文本/思考/工具参数缓冲区。
     * {@code message_delta} 事件到达时返回完整 {@link LlmChatResponse}，其余事件返回 null。
     */
    public static LlmChatResponse parseStreamEvent(String eventType, JSONObject data,
                                                   StringBuilder textBuilder,
                                                   StringBuilder thinkingBuilder,
                                                   List<LlmToolCall> toolCalls) {
        if ("content_block_start".equals(eventType)) {
            JSONObject block = data.getJSONObject("content_block");
            if (block != null && "tool_use".equals(block.getString("type"))) {
                toolCalls.add(LlmToolCall.builder()
                        .id(block.getString("id"))
                        .name(block.getString("name"))
                        .argumentsJson("{}")
                        .build());
            }
        } else if ("content_block_delta".equals(eventType)) {
            JSONObject delta = data.getJSONObject("delta");
            if (delta == null) {
                return null;
            }
            String deltaType = delta.getString("type");
            if ("text_delta".equals(deltaType)) {
                textBuilder.append(delta.getString("text"));
            } else if ("thinking_delta".equals(deltaType)) {
                thinkingBuilder.append(delta.getString("thinking"));
            } else if ("input_json_delta".equals(deltaType) && !toolCalls.isEmpty()) {
                LlmToolCall last = toolCalls.get(toolCalls.size() - 1);
                String merged = mergeToolArguments(last.getArgumentsJson(), delta.getString("partial_json"));
                toolCalls.set(toolCalls.size() - 1, LlmToolCall.builder()
                        .id(last.getId())
                        .name(last.getName())
                        .argumentsJson(merged)
                        .build());
            }
        } else if ("message_delta".equals(eventType)) {
            JSONObject delta = data.getJSONObject("delta");
            String stopReason = delta != null ? delta.getString("stop_reason") : null;
            JSONObject usageObj = data.getJSONObject("usage");
            return LlmChatResponse.builder()
                    .content(textBuilder.length() > 0 ? textBuilder.toString() : null)
                    .thinkingContent(thinkingBuilder.length() > 0 ? thinkingBuilder.toString() : null)
                    .toolCalls(toolCalls.isEmpty() ? null : new ArrayList<>(toolCalls))
                    .finishReason(stopReason)
                    .usage(parseUsage(usageObj))
                    .build();
        }
        return null;
    }

    /**
     * 将应用层消息列表转为 Anthropic messages 数组。
     * system 消息写入 systemBlocks；连续 tool 消息合并为一条 user+tool_result 消息。
     */
    static JSONArray toAnthropicMessages(List<LlmMessage> messages, List<JSONObject> systemBlocks) {
        JSONArray arr = new JSONArray();
        if (messages == null || messages.isEmpty()) {
            return arr;
        }
        int index = 0;
        while (index < messages.size()) {
            LlmMessage msg = messages.get(index);
            String role = msg.getRole();
            if ("system".equals(role)) {
                appendSystemBlock(systemBlocks, msg.getContent());
                index++;
                continue;
            }
            if ("tool".equals(role)) {
                JSONArray toolResults = new JSONArray();
                while (index < messages.size() && "tool".equals(messages.get(index).getRole())) {
                    LlmMessage toolMsg = messages.get(index);
                    JSONObject block = new JSONObject();
                    block.put("type", "tool_result");
                    block.put("tool_use_id", toolMsg.getToolCallId());
                    block.put("content", toolMsg.getContent());
                    if (toolMsg.isToolError()) {
                        block.put("is_error", true);
                    }
                    toolResults.add(block);
                    index++;
                }
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", toolResults);
                arr.add(userMsg);
                continue;
            }
            if ("user".equals(role)) {
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", toUserContent(msg));
                arr.add(userMsg);
                index++;
                continue;
            }
            if ("assistant".equals(role)) {
                JSONObject assistantMsg = new JSONObject();
                assistantMsg.put("role", "assistant");
                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    JSONArray content = new JSONArray();
                    if (msg.getContent() != null && !msg.getContent().isBlank()) {
                        content.add(textBlock(msg.getContent()));
                    }
                    for (LlmToolCall toolCall : msg.getToolCalls()) {
                        JSONObject toolUse = new JSONObject();
                        toolUse.put("type", "tool_use");
                        toolUse.put("id", toolCall.getId());
                        toolUse.put("name", toolCall.getName());
                        toolUse.put("input", parseToolInput(toolCall.getArgumentsJson()));
                        content.add(toolUse);
                    }
                    assistantMsg.put("content", content);
                } else {
                    assistantMsg.put("content", msg.getContent());
                }
                arr.add(assistantMsg);
                index++;
                continue;
            }
            index++;
        }
        return arr;
    }

    /** 构造 user 消息的 content 字段：纯字符串或多模态块数组 */
    static Object toUserContent(LlmMessage msg) {
        if (msg.getContentParts() != null && !msg.getContentParts().isEmpty()) {
            JSONArray blocks = new JSONArray();
            for (LlmContentPart part : msg.getContentParts()) {
                blocks.add(toAnthropicContentPart(part));
            }
            return blocks;
        }
        return msg.getContent();
    }

    /** 将 {@link LlmContentPart} 转为 Anthropic content block（text 或 image） */
    static JSONObject toAnthropicContentPart(LlmContentPart part) {
        if ("image".equals(part.getType())) {
            JSONObject image = new JSONObject();
            image.put("type", "image");
            JSONObject source = new JSONObject();
            if (part.getImageBase64() != null && !part.getImageBase64().isBlank()) {
                source.put("type", "base64");
                source.put("media_type", part.getImageMediaType() != null ? part.getImageMediaType() : "image/png");
                source.put("data", part.getImageBase64());
            } else if (part.getImageUrl() != null && !part.getImageUrl().isBlank()) {
                source.put("type", "url");
                source.put("url", part.getImageUrl());
            }
            image.put("source", source);
            return image;
        }
        return textBlock(part.getText());
    }

    /** 写入 system 字段：单段文本或多块数组；启用缓存时在末块附加 cache_control */
    private static void applySystem(JSONObject body, List<JSONObject> systemBlocks, boolean promptCaching) {
        if (systemBlocks.isEmpty()) {
            return;
        }
        if (promptCaching) {
            JSONObject last = systemBlocks.get(systemBlocks.size() - 1);
            last.put("cache_control", cacheControl());
            body.put("system", systemBlocks);
        } else if (systemBlocks.size() == 1) {
            body.put("system", systemBlocks.get(0).getString("text"));
        } else {
            body.put("system", systemBlocks);
        }
    }

    private static void appendSystemBlock(List<JSONObject> systemBlocks, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        systemBlocks.add(textBlock(content));
    }

    private static JSONObject textBlock(String text) {
        JSONObject block = new JSONObject();
        block.put("type", "text");
        block.put("text", text);
        return block;
    }

    private static JSONObject cacheControl() {
        JSONObject cache = new JSONObject();
        cache.put("type", "ephemeral");
        return cache;
    }

    /** 将 function 工具定义转为 Anthropic tools 数组（name + description + input_schema） */
    @SuppressWarnings("unchecked")
    private static void applyTools(JSONObject body, LlmChatRequest request) {
        if (request.getTools() == null || request.getTools().isEmpty()) {
            return;
        }
        JSONArray tools = new JSONArray();
        for (Map<String, Object> tool : request.getTools()) {
            Object functionObj = tool.get("function");
            if (!(functionObj instanceof Map<?, ?> function)) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("name", function.get("name"));
            item.put("description", function.get("description"));
            item.put("input_schema", function.get("parameters"));
            tools.add(item);
        }
        body.put("tools", tools);
        if (request.getToolChoice() != null && !request.getToolChoice().isBlank()) {
            body.put("tool_choice", toAnthropicToolChoice(request.getToolChoice()));
        }
    }

    /**
     * 转换 tool_choice 参数。
     * 支持 auto / none / any / required，以及指定工具名（type=tool）。
     */
    static JSONObject toAnthropicToolChoice(String toolChoice) {
        JSONObject choice = new JSONObject();
        if ("none".equalsIgnoreCase(toolChoice)) {
            choice.put("type", "none");
        } else if ("auto".equalsIgnoreCase(toolChoice) || toolChoice == null || toolChoice.isBlank()) {
            choice.put("type", "auto");
        } else if ("required".equalsIgnoreCase(toolChoice) || "any".equalsIgnoreCase(toolChoice)) {
            choice.put("type", "any");
        } else {
            choice.put("type", "tool");
            choice.put("name", toolChoice);
        }
        return choice;
    }

    /** 将 responseFormat 映射为 output_format（json_schema 或宽松 json object schema） */
    private static void applyResponseFormat(JSONObject body, Map<String, Object> responseFormat) {
        if (responseFormat == null || responseFormat.isEmpty()) {
            return;
        }
        String type = String.valueOf(responseFormat.get("type"));
        if ("json_schema".equals(type) && responseFormat.get("schema") != null) {
            JSONObject outputFormat = new JSONObject();
            outputFormat.put("type", "json_schema");
            outputFormat.put("schema", responseFormat.get("schema"));
            body.put("output_format", outputFormat);
            return;
        }
        if ("json_object".equals(type)) {
            JSONObject outputFormat = new JSONObject();
            outputFormat.put("type", "json_schema");
            JSONObject schema = new JSONObject();
            schema.put("type", "object");
            outputFormat.put("schema", schema);
            body.put("output_format", outputFormat);
        }
    }

    private static boolean usesStructuredOutput(Map<String, Object> responseFormat) {
        return responseFormat != null && !responseFormat.isEmpty();
    }

    /** 写入 Extended Thinking 配置（type=enabled + budget_tokens） */
    private static void applyThinking(JSONObject body, LlmChatRequest request) {
        if (!request.isExtendedThinking()) {
            return;
        }
        JSONObject thinking = new JSONObject();
        thinking.put("type", "enabled");
        int budget = request.getThinkingBudgetTokens() != null && request.getThinkingBudgetTokens() > 0
                ? request.getThinkingBudgetTokens()
                : 8192;
        thinking.put("budget_tokens", budget);
        body.put("thinking", thinking);
    }

    private static JSONObject parseToolInput(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(argumentsJson);
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    private static void appendContentBlocks(JSONArray content, StringBuilder textBuilder,
                                            StringBuilder thinkingBuilder, List<LlmToolCall> toolCalls) {
        if (content == null) {
            return;
        }
        for (int i = 0; i < content.size(); i++) {
            JSONObject block = content.getJSONObject(i);
            String type = block.getString("type");
            if ("text".equals(type)) {
                if (textBuilder.length() > 0) {
                    textBuilder.append('\n');
                }
                textBuilder.append(block.getString("text"));
            } else if ("thinking".equals(type)) {
                if (thinkingBuilder.length() > 0) {
                    thinkingBuilder.append('\n');
                }
                thinkingBuilder.append(block.getString("thinking"));
            } else if ("tool_use".equals(type)) {
                JSONObject input = block.getJSONObject("input");
                toolCalls.add(LlmToolCall.builder()
                        .id(block.getString("id"))
                        .name(block.getString("name"))
                        .argumentsJson(input != null ? input.toJSONString() : "{}")
                        .build());
            }
        }
    }

    /** 解析 Anthropic usage 对象为 {@link LlmUsage} */
    static LlmUsage parseUsage(JSONObject usageObj) {
        if (usageObj == null) {
            return null;
        }
        return LlmUsage.builder()
                .inputTokens(usageObj.getInteger("input_tokens"))
                .outputTokens(usageObj.getInteger("output_tokens"))
                .cacheReadTokens(usageObj.getInteger("cache_read_input_tokens"))
                .cacheCreationTokens(usageObj.getInteger("cache_creation_input_tokens"))
                .build();
    }

    private static String mergeToolArguments(String current, String partialJson) {
        String base = current == null || current.isBlank() ? "{}" : current;
        String part = partialJson == null ? "" : partialJson;
        if ("{}".equals(base)) {
            return part.isBlank() ? "{}" : part;
        }
        if (part.isBlank()) {
            return base;
        }
        if (base.endsWith("}") && part.startsWith("{")) {
            return base.substring(0, base.length() - 1) + "," + part.substring(1);
        }
        return base + part;
    }
}
