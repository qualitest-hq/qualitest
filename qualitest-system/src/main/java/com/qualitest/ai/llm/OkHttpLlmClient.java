package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.config.AiLlmConfigService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Chat Completions 协议 HTTP 客户端。
 * <p>
 * 职责：
 * <ul>
 *   <li>POST {@code /chat/completions}，Bearer 鉴权</li>
 *   <li>组装 messages、tools、response_format，支持多模态 content 数组</li>
 *   <li>解析 choices[0].message 的 content、tool_calls 与 usage</li>
 *   <li>同步与 SSE 流式两种模式，429/502/503 退避重试</li>
 * </ul>
 */
@Slf4j
@Component
public class OkHttpLlmClient {

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final AiLlmConfigService aiLlmConfigService;
    /** 进程内共享连接池，按单次请求覆盖超时 */
    private volatile OkHttpClient sharedClient;

    public OkHttpLlmClient(AiLlmConfigService aiLlmConfigService) {
        this.aiLlmConfigService = aiLlmConfigService;
    }

    /**
     * 同步对话请求。
     * request.stream=true 时内部走流式聚合后返回完整结果。
     */
    public LlmChatResponse chatSync(LlmModelConfig cfg, LlmChatRequest request) {
        if (request.isStream()) {
            StringBuilder text = new StringBuilder();
            StringBuilder thinking = new StringBuilder();
            LlmChatResponse[] holder = new LlmChatResponse[1];
            chatStream(cfg, request, new LlmStreamCallback() {
                @Override
                public void onTextDelta(String delta) {
                    text.append(delta);
                }

                @Override
                public void onThinkingDelta(String delta) {
                    thinking.append(delta);
                }

                @Override
                public void onComplete(LlmChatResponse response) {
                    holder[0] = response;
                }
            });
            if (holder[0] != null) {
                logUsage(cfg, holder[0]);
                return holder[0];
            }
            return LlmChatResponse.builder().content(text.toString()).build();
        }
        OkHttpClient client = clientFor(cfg);
        JSONObject body = buildBody(cfg, request);
        Request httpRequest = new Request.Builder()
                .url(normalizeUrl(cfg.getBaseUrl()) + "/chat/completions")
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA))
                .build();
        LlmChatResponse response = executeWithRetry(client, httpRequest);
        logUsage(cfg, response);
        return response;
    }

    /** SSE 流式对话，解析 data: 行并通过 callback 推送文本增量 */
    public void chatStream(LlmModelConfig cfg, LlmChatRequest request, LlmStreamCallback callback) {
        OkHttpClient client = clientFor(cfg);
        JSONObject body = buildBody(cfg, request.toBuilder().stream(true).build());
        Request httpRequest = new Request.Builder()
                .url(normalizeUrl(cfg.getBaseUrl()) + "/chat/completions")
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA))
                .build();
        StringBuilder textBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        StreamToolCallAccumulator toolCallAccumulator = new StreamToolCallAccumulator();
        String finishReason = null;
        LlmUsage usage = null;
        try (Response response = client.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new LlmClientException("LLM 流式请求失败 HTTP " + response.code() + ": "
                        + truncate(responseBody, 500));
            }
            if (response.body() == null) {
                throw new LlmClientException("LLM 流式响应为空");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.isBlank() || "[DONE]".equals(payload)) {
                        continue;
                    }
                    JSONObject root = JSON.parseObject(payload);
                    JSONArray choices = root.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject delta = choice.getJSONObject("delta");
                        if (delta != null) {
                            JSONArray tcDelta = delta.getJSONArray("tool_calls");
                            if (tcDelta != null && !tcDelta.isEmpty()) {
                                toolCallAccumulator.appendDelta(tcDelta);
                            }
                            if (delta.getString("content") != null) {
                                String deltaText = delta.getString("content");
                                textBuilder.append(deltaText);
                                callback.onTextDelta(deltaText);
                            }
                            String reasoningDelta = delta.getString("reasoning_content");
                            if (reasoningDelta != null && !reasoningDelta.isEmpty()) {
                                thinkingBuilder.append(reasoningDelta);
                                callback.onThinkingDelta(reasoningDelta);
                            }
                        }
                        if (choice.getString("finish_reason") != null) {
                            finishReason = choice.getString("finish_reason");
                        }
                    }
                    JSONObject usageObj = root.getJSONObject("usage");
                    if (usageObj != null) {
                        usage = parseUsage(usageObj);
                    }
                }
            }
            List<LlmToolCall> toolCalls = toolCallAccumulator.build();
            LlmChatResponse completed = LlmChatResponse.builder()
                    .content(textBuilder.length() > 0 ? textBuilder.toString() : null)
                    .thinkingContent(thinkingBuilder.length() > 0 ? thinkingBuilder.toString() : null)
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                    .finishReason(finishReason)
                    .usage(usage)
                    .build();
            logUsage(cfg, completed);
            callback.onComplete(completed);
        } catch (IOException e) {
            throw new LlmClientException("LLM 流式请求失败: " + e.getMessage(), e);
        }
    }

    private OkHttpClient clientFor(LlmModelConfig cfg) {
        if (sharedClient == null) {
            synchronized (this) {
                if (sharedClient == null) {
                    sharedClient = new OkHttpClient.Builder()
                            .connectTimeout(aiLlmConfigService.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                            .readTimeout(aiLlmConfigService.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                            .writeTimeout(aiLlmConfigService.getWriteTimeoutMs(), TimeUnit.MILLISECONDS)
                            .addInterceptor(new RetryInterceptor())
                            .build();
                }
            }
        }
        return sharedClient.newBuilder()
                .connectTimeout(cfg.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(cfg.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(cfg.getWriteTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    /** 构造 chat/completions 请求体 */
    private JSONObject buildBody(LlmModelConfig cfg, LlmChatRequest request) {
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
        if (request.isReasoningEnabled()) {
            body.put("reasoning_effort", "medium");
        }
        if (request.isStream()) {
            body.put("stream", true);
            body.put("stream_options", new JSONObject().fluentPut("include_usage", true));
        }
        return body;
    }

    /**
     * 转换 tool_choice。
     * auto/none 原样传递；工具名转为 {@code {type:function, function:{name}}} 对象。
     */
    private Object toOpenAiToolChoice(String toolChoice) {
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

    /** 将应用层消息列表转为 messages 数组，支持 tool_calls 与多模态 content */
    private JSONArray toMessageArray(List<LlmMessage> messages) {
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

    /** 将 LlmContentPart 列表转为 content 数组（text + image_url） */
    private JSONArray toOpenAiContentParts(List<LlmContentPart> parts) {
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

    private LlmChatResponse executeWithRetry(OkHttpClient client, Request request) {
        int maxAttempts = 3;
        IOException lastIo = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    return parseResponse(responseBody);
                }
                int code = response.code();
                if (code == 429 || code == 502 || code == 503) {
                    sleepBackoff(attempt);
                    continue;
                }
                throw new LlmClientException("LLM 请求失败 HTTP " + code + ": " + truncate(responseBody, 500));
            } catch (IOException e) {
                lastIo = e;
                sleepBackoff(attempt);
            }
        }
        throw new LlmClientException("LLM 请求失败: " + (lastIo != null ? lastIo.getMessage() : "未知错误"), lastIo);
    }

    private LlmChatResponse parseResponse(String responseBody) {
        JSONObject root = JSON.parseObject(responseBody);
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmClientException("LLM 响应缺少 choices");
        }
        JSONObject choice = choices.getJSONObject(0);
        String finishReason = choice.getString("finish_reason");
        JSONObject message = choice.getJSONObject("message");
        if (message == null) {
            throw new LlmClientException("LLM 响应缺少 message");
        }
        String content = message.getString("content");
        String reasoningContent = message.getString("reasoning_content");
        List<LlmToolCall> toolCalls = new ArrayList<>();
        JSONArray tcArr = message.getJSONArray("tool_calls");
        if (tcArr != null) {
            for (int i = 0; i < tcArr.size(); i++) {
                JSONObject tc = tcArr.getJSONObject(i);
                JSONObject fn = tc.getJSONObject("function");
                toolCalls.add(LlmToolCall.builder()
                        .id(tc.getString("id"))
                        .name(fn != null ? fn.getString("name") : null)
                        .argumentsJson(fn != null ? fn.getString("arguments") : "{}")
                        .build());
            }
        }
        return LlmChatResponse.builder()
                .content(content)
                .thinkingContent(reasoningContent)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .finishReason(finishReason)
                .usage(parseUsage(root.getJSONObject("usage")))
                .build();
    }

    private LlmUsage parseUsage(JSONObject usageObj) {
        if (usageObj == null) {
            return null;
        }
        return LlmUsage.builder()
                .inputTokens(usageObj.getInteger("prompt_tokens"))
                .outputTokens(usageObj.getInteger("completion_tokens"))
                .build();
    }

    /** 写入 Token 用量 INFO 日志 */
    private void logUsage(LlmModelConfig cfg, LlmChatResponse response) {
        if (response == null || response.getUsage() == null) {
            return;
        }
        LlmUsage usage = response.getUsage();
        log.info("OpenAI usage modelId={} input={} output={}",
                cfg.getAiLlmModelId(), usage.getInputTokens(), usage.getOutputTokens());
    }

    private static String normalizeUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LlmClientException("厂商 base_url 未配置");
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep((long) Math.pow(2, attempt) * 500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static class RetryInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            return chain.proceed(chain.request());
        }
    }

    /**
     * 聚合 OpenAI 流式 delta.tool_calls 分片（按 index 拼接 id/name/arguments）。
     */
    static final class StreamToolCallAccumulator {
        private final Map<Integer, StreamToolCallBuilder> builders = new TreeMap<>();

        void appendDelta(JSONArray tcDelta) {
            for (int i = 0; i < tcDelta.size(); i++) {
                JSONObject item = tcDelta.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                int index = item.containsKey("index") ? item.getIntValue("index") : i;
                StreamToolCallBuilder builder = builders.computeIfAbsent(index, k -> new StreamToolCallBuilder());
                if (item.getString("id") != null) {
                    builder.id = item.getString("id");
                }
                JSONObject fn = item.getJSONObject("function");
                if (fn != null) {
                    if (fn.getString("name") != null) {
                        builder.name = fn.getString("name");
                    }
                    if (fn.getString("arguments") != null) {
                        builder.argumentsBuilder.append(fn.getString("arguments"));
                    }
                }
            }
        }

        List<LlmToolCall> build() {
            List<LlmToolCall> result = new ArrayList<>();
            for (StreamToolCallBuilder builder : builders.values()) {
                result.add(LlmToolCall.builder()
                        .id(builder.id)
                        .name(builder.name)
                        .argumentsJson(builder.argumentsBuilder.length() > 0
                                ? builder.argumentsBuilder.toString()
                                : "{}")
                        .build());
            }
            return result;
        }
    }

    private static final class StreamToolCallBuilder {
        private String id;
        private String name;
        private final StringBuilder argumentsBuilder = new StringBuilder();
    }
}
