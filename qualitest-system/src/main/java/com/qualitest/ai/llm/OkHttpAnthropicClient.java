package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.config.AiLlmConfigService;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.TimeUnit;

/**
 * Anthropic Messages API HTTP 客户端。
 * <p>
 * 职责：
 * <ul>
 *   <li>POST {@code /v1/messages}，携带 x-api-key、anthropic-version 与 anthropic-beta 头</li>
 *   <li>同步与 SSE 流式两种调用模式</li>
 *   <li>429/502/503 与 IO 异常的指数退避重试</li>
 *   <li>记录 Token 用量日志</li>
 * </ul>
 */
@Slf4j
@Component
public class OkHttpAnthropicClient {

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final AiLlmConfigService aiLlmConfigService;
    /** 进程内共享连接池，按单次请求覆盖超时 */
    private volatile OkHttpClient sharedClient;

    public OkHttpAnthropicClient(AiLlmConfigService aiLlmConfigService) {
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
            List<LlmToolCall> toolCalls = new ArrayList<>();
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
            return LlmChatResponse.builder()
                    .content(text.length() > 0 ? text.toString() : null)
                    .thinkingContent(thinking.length() > 0 ? thinking.toString() : null)
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                    .build();
        }
        OkHttpClient client = clientFor(cfg);
        JSONObject body = AnthropicPayloadBuilder.buildBody(cfg, request);
        Request httpRequest = buildRequest(cfg, body, request);
        LlmChatResponse response = executeWithRetry(client, httpRequest);
        logUsage(cfg, response);
        return response;
    }

    /** SSE 流式对话，逐事件解析并通过 callback 推送增量 */
    public void chatStream(LlmModelConfig cfg, LlmChatRequest request, LlmStreamCallback callback) {
        OkHttpClient client = clientFor(cfg);
        JSONObject body = AnthropicPayloadBuilder.buildBody(cfg, request.toBuilder().stream(true).build());
        Request httpRequest = buildRequest(cfg, body, request);
        StringBuilder textBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        List<LlmToolCall> toolCalls = new ArrayList<>();
        try (Response response = client.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new LlmClientException("Anthropic 流式请求失败 HTTP " + response.code() + ": "
                        + truncate(responseBody, 500));
            }
            if (response.body() == null) {
                throw new LlmClientException("Anthropic 流式响应为空");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                String line;
                String eventType = null;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        eventType = line.substring(6).trim();
                        continue;
                    }
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String dataJson = line.substring(5).trim();
                    if (dataJson.isBlank()) {
                        continue;
                    }
                    JSONObject data = JSON.parseObject(dataJson);
                    LlmChatResponse completed = AnthropicPayloadBuilder.parseStreamEvent(
                            eventType, data, textBuilder, thinkingBuilder, toolCalls);
                    if ("content_block_delta".equals(eventType)) {
                        JSONObject delta = data.getJSONObject("delta");
                        if (delta != null) {
                            if ("text_delta".equals(delta.getString("type"))) {
                                callback.onTextDelta(delta.getString("text"));
                            } else if ("thinking_delta".equals(delta.getString("type"))) {
                                callback.onThinkingDelta(delta.getString("thinking"));
                            }
                        }
                    }
                    if (completed != null) {
                        logUsage(cfg, completed);
                        callback.onComplete(completed);
                        return;
                    }
                }
            }
            LlmChatResponse fallback = LlmChatResponse.builder()
                    .content(textBuilder.length() > 0 ? textBuilder.toString() : null)
                    .thinkingContent(thinkingBuilder.length() > 0 ? thinkingBuilder.toString() : null)
                    .toolCalls(toolCalls.isEmpty() ? null : new ArrayList<>(toolCalls))
                    .build();
            logUsage(cfg, fallback);
            callback.onComplete(fallback);
        } catch (IOException e) {
            throw new LlmClientException("Anthropic 流式请求失败: " + e.getMessage(), e);
        }
    }

    /** 组装 HTTP 请求：URL、鉴权头、anthropic-version 与 anthropic-beta */
    private Request buildRequest(LlmModelConfig cfg, JSONObject body, LlmChatRequest request) {
        String url = normalizeUrl(cfg.getBaseUrl()) + "/v1/messages";
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("x-api-key", cfg.getApiKey())
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .header("anthropic-version", aiLlmConfigService.getAnthropicVersion())
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA));
        List<String> betas = AnthropicPayloadBuilder.resolveBetaHeaders(request);
        if (!betas.isEmpty()) {
            builder.header("anthropic-beta", String.join(",", betas));
        }
        return builder.build();
    }

    private OkHttpClient clientFor(LlmModelConfig cfg) {
        if (sharedClient == null) {
            synchronized (this) {
                if (sharedClient == null) {
                    sharedClient = new OkHttpClient.Builder()
                            .connectTimeout(aiLlmConfigService.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                            .readTimeout(aiLlmConfigService.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                            .writeTimeout(aiLlmConfigService.getWriteTimeoutMs(), TimeUnit.MILLISECONDS)
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
                    return AnthropicPayloadBuilder.parseResponse(responseBody);
                }
                int code = response.code();
                if (code == 429 || code == 502 || code == 503) {
                    sleepBackoff(attempt);
                    continue;
                }
                throw new LlmClientException("Anthropic 请求失败 HTTP " + code + ": " + truncate(responseBody, 500));
            } catch (IOException e) {
                lastIo = e;
                sleepBackoff(attempt);
            }
        }
        throw new LlmClientException("Anthropic 请求失败: " + (lastIo != null ? lastIo.getMessage() : "未知错误"), lastIo);
    }

    /** 写入 Token 用量 INFO 日志 */
    private void logUsage(LlmModelConfig cfg, LlmChatResponse response) {
        if (response == null || response.getUsage() == null) {
            return;
        }
        LlmUsage usage = response.getUsage();
        log.info("Anthropic usage modelId={} input={} output={} cacheRead={} cacheCreate={}",
                cfg.getAiLlmModelId(),
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getCacheReadTokens(),
                usage.getCacheCreationTokens());
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
}
