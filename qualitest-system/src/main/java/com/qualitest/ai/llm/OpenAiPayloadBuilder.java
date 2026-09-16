package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

/**
 * Chat Completions 请求体组装：把应用层消息、工具与思考开关转成上游 JSON。
 * <p>
 * 只做字段转换，不发 HTTP。
 */
public final class OpenAiPayloadBuilder {

    private OpenAiPayloadBuilder() {
    }

    /**
     * 组装一次对话请求体：model、max_tokens、messages，以及可选的 tools、
     * tool_choice、response_format、思考开关与 stream。
     *
     * @param cfg     模型运行时配置（含 model 名与思考请求风格）
     * @param request 本轮对话请求
     * @return 可直接 POST 的 JSON 对象
     */
    public static JSONObject buildBody(LlmModelConfig cfg, LlmChatRequest request) {
        JSONObject body = new JSONObject();
        body.put("model", cfg.getModelName());
        body.put("max_tokens", cfg.getMaxTokens());
        body.put("messages", toMessageArray(request.getMessages()));
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", request.getTools());
            if (request.getToolChoice() != null && !request.getToolChoice().isBlank()) {
                body.put("tool_choice", toOpenAiToolChoice(request.getToolChoice()));
            }
        }
        if (request.getResponseFormat() != null) {
            body.put("response_format", request.getResponseFormat());
        }
        applyThinkingControl(body, cfg, request);
        if (request.isStream()) {
            body.put("stream", true);
            body.put("stream_options", new JSONObject().fluentPut("include_usage", true));
        }
        return body;
    }

    /**
     * 按模型思考请求风格写入思考相关字段。
     * <ul>
     *   <li>风格为空：不写任何思考字段</li>
     *   <li>{@code deepseek_thinking}：写入 {@code thinking.type}（开为 enabled，关为 disabled）；
     *       开启时再带 {@code reasoning_effort}</li>
     *   <li>{@code openai_reasoning_effort}：仅开启时写入 {@code reasoning_effort}</li>
     * </ul>
     *
     * @param body    正在组装的请求体
     * @param cfg     模型配置，读取 thinkingControl
     * @param request 本轮是否开启思考（reasoningEnabled）
     */
    private static void applyThinkingControl(JSONObject body, LlmModelConfig cfg, LlmChatRequest request) {
        String style = cfg != null ? cfg.getThinkingControl() : null;
        if (style == null || style.isBlank()) {
            return;
        }
        boolean enabled = request != null && request.isReasoningEnabled();
        if (ThinkingControlStyles.DEEPSEEK_THINKING.equals(style)) {
            JSONObject thinking = new JSONObject();
            thinking.put("type", enabled ? "enabled" : "disabled");
            body.put("thinking", thinking);
            if (enabled) {
                body.put("reasoning_effort", "medium");
            }
            return;
        }
        if (ThinkingControlStyles.OPENAI_REASONING_EFFORT.equals(style) && enabled) {
            body.put("reasoning_effort", "medium");
        }
    }

    /**
     * 转换 tool_choice：auto/none 原样返回；其它字符串转为带 function.name 的对象。
     *
     * @param toolChoice 工具选择策略或具体工具名
     * @return 字符串或 JSON 对象
     */
    public static Object toOpenAiToolChoice(String toolChoice) {
        if ("auto".equalsIgnoreCase(toolChoice) || "none".equalsIgnoreCase(toolChoice)) {
            return toolChoice;
        }
        JSONObject choice = new JSONObject();
        choice.put("type", "function");
        JSONObject function = new JSONObject();
        function.put("name", toolChoice);
        choice.put("function", function);
        return choice;
    }

    /**
     * 将应用层消息列表转为 messages 数组。
     * 支持纯文本 content、多模态 content 数组、tool_call_id 与 tool_calls。
     *
     * @param messages 应用层消息，可为 null
     * @return 上游 messages 数组，不会为 null
     */
    public static JSONArray toMessageArray(List<LlmMessage> messages) {
        JSONArray arr = new JSONArray();
        if (messages == null) {
            return arr;
        }
        for (LlmMessage msg : messages) {
            JSONObject item = new JSONObject();
            item.put("role", msg.getRole());
            if (msg.getContentParts() != null && !msg.getContentParts().isEmpty()) {
                item.put("content", toOpenAiContentParts(msg.getContentParts()));
            } else if (msg.getContent() != null) {
                item.put("content", msg.getContent());
            }
            if (msg.getToolCallId() != null) {
                item.put("tool_call_id", msg.getToolCallId());
            }
            if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                JSONArray toolCalls = new JSONArray();
                for (LlmToolCall tc : msg.getToolCalls()) {
                    JSONObject tcObj = new JSONObject();
                    tcObj.put("id", tc.getId());
                    tcObj.put("type", "function");
                    JSONObject fn = new JSONObject();
                    fn.put("name", tc.getName());
                    fn.put("arguments", tc.getArgumentsJson());
                    tcObj.put("function", fn);
                    toolCalls.add(tcObj);
                }
                item.put("tool_calls", toolCalls);
            }
            arr.add(item);
        }
        return arr;
    }

    /**
     * 将内容分片转为 content 数组：文本块为 type=text，图片块为 type=image_url。
     * 图片优先用 URL；否则用 base64 拼 data URL。
     *
     * @param parts 内容分片列表
     * @return content 数组
     */
    public static JSONArray toOpenAiContentParts(List<LlmContentPart> parts) {
        JSONArray arr = new JSONArray();
        for (LlmContentPart part : parts) {
            if ("image".equals(part.getType())) {
                JSONObject image = new JSONObject();
                image.put("type", "image_url");
                JSONObject imageUrl = new JSONObject();
                if (part.getImageUrl() != null && !part.getImageUrl().isBlank()) {
                    imageUrl.put("url", part.getImageUrl());
                } else if (part.getImageBase64() != null && !part.getImageBase64().isBlank()) {
                    String mediaType = part.getImageMediaType() != null ? part.getImageMediaType() : "image/png";
                    imageUrl.put("url", "data:" + mediaType + ";base64," + part.getImageBase64());
                }
                image.put("image_url", imageUrl);
                arr.add(image);
            } else {
                JSONObject text = new JSONObject();
                text.put("type", "text");
                text.put("text", part.getText());
                arr.add(text);
            }
        }
        return arr;
    }
}
