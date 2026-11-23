package com.qualitest.plugin.channel;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.plugin.PluginErrorCode;
import com.qualitest.plugin.PluginException;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 经被测旁执行代理代跑与推送：探活、远程执行、安装制品、写入 bind。
 */
@Component
public class ExecAgentChannel {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 探活：GET {baseUrl}/ping。
     */
    public PingResult ping(String baseUrl, String token) {
        String url = join(baseUrl, "/ping");
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            applyToken(b, token);
            HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 401) {
                throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE, "执行代理鉴权失败");
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE,
                        "执行代理 ping HTTP " + resp.statusCode());
            }
            JSONObject json = JSON.parseObject(resp.body());
            boolean ok = json != null && Boolean.TRUE.equals(json.getBoolean("ok"));
            if (!ok) {
                throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE, "执行代理 ping 未返回 ok");
            }
            return PingResult.builder()
                    .ok(true)
                    .version(json.getString("version"))
                    .hostname(json.getString("hostname"))
                    .raw(resp.body())
                    .build();
        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE,
                    "执行代理不可达: " + e.getMessage(), e);
        }
    }

    /**
     * 代跑业务 CLI：POST {baseUrl}/exec。
     */
    public LocalExecChannel.ExecResult exec(
            String baseUrl, String token, String bin, List<String> args, String configText, long timeoutMs) {
        JSONObject body = new JSONObject();
        body.put("bin", bin);
        body.put("args", args == null ? List.of() : args);
        if (configText != null) {
            body.put("configText", configText);
        }
        body.put("timeoutMs", timeoutMs > 0 ? timeoutMs : 120_000L);
        JSONObject json = postJson(baseUrl, token, "/exec", body, timeoutMs > 0 ? timeoutMs + 15_000L : 135_000L);
        int exit = json.getIntValue("exitCode", -1);
        return LocalExecChannel.ExecResult.builder()
                .exitCode(exit)
                .stdout(json.getString("stdout") == null ? "" : json.getString("stdout"))
                .stderr(firstNonBlank(json.getString("stderrTail"), json.getString("stderr"), json.getString("error")))
                .build();
    }

    /**
     * 推送安装业务 CLI：POST {baseUrl}/install。
     */
    public JSONObject install(String baseUrl, String token, Map<String, Object> payload) {
        return postJson(baseUrl, token, "/install", payload, 360_000L);
    }

    /**
     * 推送写 bind：POST {baseUrl}/bind-write。
     */
    public JSONObject bindWrite(String baseUrl, String token, Map<String, Object> payload) {
        return postJson(baseUrl, token, "/bind-write", payload, 60_000L);
    }

    private JSONObject postJson(String baseUrl, String token, String path, Object payload, long timeoutMs) {
        String url = join(baseUrl, path);
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(timeoutMs, 5_000L)))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload), StandardCharsets.UTF_8));
            applyToken(b, token);
            HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 401) {
                throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE, "执行代理鉴权失败");
            }
            JSONObject json = JSON.parseObject(resp.body());
            if (json == null) {
                throw new PluginException(PluginErrorCode.INVOKE_FAILED,
                        "执行代理响应非 JSON: HTTP " + resp.statusCode());
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new PluginException(PluginErrorCode.INVOKE_FAILED,
                        "执行代理 HTTP " + resp.statusCode() + ": " + firstNonBlank(json.getString("error"), resp.body()));
            }
            if (Boolean.FALSE.equals(json.getBoolean("ok"))) {
                throw new PluginException(PluginErrorCode.INVOKE_FAILED,
                        firstNonBlank(json.getString("error"), "执行代理返回失败"));
            }
            return json;
        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginException(PluginErrorCode.AGENT_UNREACHABLE,
                    "执行代理请求失败: " + e.getMessage(), e);
        }
    }

    private static void applyToken(HttpRequest.Builder b, String token) {
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token.trim());
        }
    }

    private static String join(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new PluginException(PluginErrorCode.CONFIG_SCHEMA_INVALID, "exec-agent baseUrl 为空");
        }
        String base = baseUrl.trim().replaceAll("/+$", "");
        return base + path;
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return "";
        }
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return "";
    }

    /**
     * 执行代理探活结果。
     */
    @Getter
    @Builder
    public static class PingResult {
        /** 探活是否成功 */
        private final boolean ok;
        /** 代理报告的版本号 */
        private final String version;
        /** 代理所在主机名 */
        private final String hostname;
        /** 原始响应正文 */
        private final String raw;
    }
}
