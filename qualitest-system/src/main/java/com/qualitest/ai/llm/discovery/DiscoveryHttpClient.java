package com.qualitest.ai.llm.discovery;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.llm.LlmClientException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 模型发现专用 HTTP GET 客户端：短超时、JSON 响应校验、统一错误映射。
 */
@Slf4j
final class DiscoveryHttpClient {

    private DiscoveryHttpClient() {
    }

    /**
     * 发起 GET 请求并返回响应体字符串。
     *
     * @param url     完整请求 URL
     * @param context 超时配置
     * @param builder 可追加鉴权头等；url 由本方法设置
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
                throw mapHttpError(response.code(), body);
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

    /** 将 HTTP 状态码映射为可读的业务异常消息 */
    private static LlmClientException mapHttpError(int code, String body) {
        return switch (code) {
            case 401, 403 -> new LlmClientException("API Key 无效或无权访问");
            case 404 -> new LlmClientException("接口地址错误，未找到模型列表端点");
            default -> new LlmClientException("请求失败（HTTP " + code + "）");
        };
    }
}
