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
 * 发送 POST /chat/completions（Bearer 鉴权）；请求 JSON 由 OpenAiPayloadBuilder 组装；
 * 解析 choices 中的正文、工具调用与用量；支持同步与 SSE 流式，并对 429/502/503 退避重试。
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
     * 同步对话：组装请求、执行并解析完整响应。
     * 若 request.stream=true，则内部走流式并聚合成一次完整结果返回。
     * 本轮未开思考时，会去掉响应里残留的思考文本。
     *
     * @param cfg     模型运行时配置
     * @param request 本轮对话请求
     * @return 解析后的完整响应
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
        Request httpRequest = buildChatCompletionsRequest(cfg, OpenAiPayloadBuilder.buildBody(cfg, request));
        LlmChatResponse response = executeWithRetry(client, httpRequest);
        response = dropThinkingIfDisabled(request, response);
        logUsage(cfg, response);
        return response;
    }

    /**
     * SSE 流式对话：按 data: 行解析增量，通过回调推送正文与（若本轮开思考）思考增量，
     * 结束后回调完整结果。本轮未开思考时忽略 reasoning_content，不推送、不写入结果。
     *
     * @param cfg      模型运行时配置
     * @param request  本轮对话请求（内部会强制 stream=true）
     * @param callback 文本/思考增量与完成回调
     */
    public void chatStream(LlmModelConfig cfg, LlmChatRequest request, LlmStreamCallback callback) {
        OkHttpClient client = clientFor(cfg);
        LlmChatRequest streamRequest = request.toBuilder().stream(true).build();
        Request httpRequest = buildChatCompletionsRequest(cfg, OpenAiPayloadBuilder.buildBody(cfg, streamRequest));
        StringBuilder textBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        StreamToolCallAccumulator toolCallAccumulator = new StreamToolCallAccumulator();
        String finishReason = null;
        LlmUsage usage = null;
        // 本轮关思考时不接收、不转发 reasoning_content
        boolean acceptThinking = streamRequest.isReasoningEnabled();
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
                            if (acceptThinking) {
                                String reasoningDelta = delta.getString("reasoning_content");
                                if (reasoningDelta != null && !reasoningDelta.isEmpty()) {
                                    thinkingBuilder.append(reasoningDelta);
                                    callback.onThinkingDelta(reasoningDelta);
                                }
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

    /**
     * 构造 chat/completions 的 HTTP 请求：拼 URL、Bearer 头与 JSON Body。
     *
     * @param cfg  模型配置（baseUrl、apiKey）
     * @param body 已组装好的请求体
     * @return OkHttp Request
     */
    private Request buildChatCompletionsRequest(LlmModelConfig cfg, JSONObject body) {
        return new Request.Builder()
                .url(normalizeUrl(cfg.getBaseUrl()) + "/chat/completions")
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA))
                .build();
    }

    /**
     * 本轮未开启思考时，清空响应中的思考文本，避免界面误展示。
     *
     * @param request  本轮请求（看 reasoningEnabled）
     * @param response 上游解析结果
     * @return 去掉思考字段后的响应，或原对象
     */
    private static LlmChatResponse dropThinkingIfDisabled(LlmChatRequest request, LlmChatResponse response) {
        if (request.isReasoningEnabled() || response == null || response.getThinkingContent() == null) {
            return response;
        }
        return response.toBuilder().thinkingContent(null).build();
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
