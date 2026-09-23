package com.qualitest.ai.llm.discovery;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.llm.LlmUpstreamErrorMessages;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 模型发现专用的 HTTP GET 客户端。
 * <p>
 * 按上下文超时发起请求；成功时校验响应体为非空 JSON 并返回原文；
 * 失败时按状态码或网络错误抛出中文业务异常。
 */
@Slf4j
final class DiscoveryHttpClient {

    private DiscoveryHttpClient() {
    }

    /**
     * 发起 GET 并返回响应体字符串。
     * 非 2xx：按状态码映射为业务异常；空体或非 JSON：分别提示空响应 / 协议或地址错误；
     * IO 失败：提示网络超时或连接失败。
     *
     * @param url     完整请求 URL
     * @param context 连接超时、读超时等
     * @param builder 可追加鉴权头等；最终 url 由本方法写入
     * @return 响应体原文（已确认为可解析 JSON）
     */
    static String get(String url, ModelDiscoveryContext context, Request.Builder builder) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(context.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(context.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
        Request request = builder.url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw mapHttpError(response.code());
            }
            if (body.isBlank()) {
                throw new LlmClientException("远端返回空响应");
            }
            try {
                JSON.parse(body);
            } catch (Exception ex) {
                throw new LlmClientException("非 JSON 响应，请检查接口地址或协议类型");
            }
            return body;
        } catch (LlmClientException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("模型发现请求失败: {}", ex.getMessage());
            throw new LlmClientException("网络超时或连接失败，请检查接口地址或代理");
        }
    }

    /**
     * 将 HTTP 状态码转为业务异常。
     * 已知码（401/403/404/408/504/429）用固定中文；其余为「请求失败（HTTP 码）」。
     *
     * @param code HTTP 状态码
     * @return 带中文说明的业务异常
     */
    private static LlmClientException mapHttpError(int code) {
        String mapped = LlmUpstreamErrorMessages.forHttpStatus(code);
        if (mapped != null) {
            return new LlmClientException(mapped);
        }
        return new LlmClientException("请求失败（HTTP " + code + "）");
    }
}
