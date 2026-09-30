package com.qualitest.flow.snapshot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.HttpEgressAllowlist;
import com.qualitest.flow.exception.FlowErrorCode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通过 HTTP 调用被测系统的 snapshot、restore 接口。
 * <p>
 * 请求体为 JSON；同步阻塞直到响应或超时。不单独做探活，snapshot 失败即视为被测快照服务不可用。
 * 发起请求前校验 reset 端点 URL（协议、主机，以及可选的出口白名单）。
 */
@Component
public class HttpEndpointSnapshotAdapter implements DbSnapshotAdapter {

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    /** 默认 HTTP 客户端，连接/读/写超时各 30 秒 */
    private final OkHttpClient httpClient;
    /** HTTP 出站目标校验（协议 / 主机 / 可选白名单） */
    private final HttpEgressAllowlist httpEgressAllowlist;

    /**
     * @param httpEgressAllowlist 出站 URL 校验（协议/主机；白名单开启时再核对 host:port）
     */
    public HttpEndpointSnapshotAdapter(HttpEgressAllowlist httpEgressAllowlist) {
        this.httpEgressAllowlist = httpEgressAllowlist;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public SnapshotRef snapshot(SnapshotRequest request) {
        validateBase(request.getResetEndpointBase());
        OkHttpClient client = clientFor(request.getTimeoutMs());
        String url = request.getResetEndpointBase() + "/snapshot";

        Map<String, Object> body = new HashMap<>();
        body.put("scope", request.getScope());
        if (request.getTables() != null && !request.getTables().isEmpty()) {
            body.put("tables", request.getTables());
        }
        body.put("label", request.getLabel());
        if (request.getMeta() != null && !request.getMeta().isEmpty()) {
            body.put("meta", request.getMeta());
        }

        Request httpRequest = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON.toJSONString(body), JSON_MEDIA))
                .build();

        try (Response response = client.newCall(httpRequest).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                throw parseRemoteError(FlowErrorCode.TF_SNAPSHOT_FAILED, response.code(), responseBody);
            }
            JSONObject json = JSON.parseObject(responseBody);
            if (json == null || json.getString("snapshotId") == null || json.getString("snapshotId").isBlank()) {
                throw new SnapshotException(FlowErrorCode.TF_SNAPSHOT_FAILED, "snapshot 响应缺少 snapshotId");
            }
            return SnapshotRef.builder()
                    .snapshotId(json.getString("snapshotId"))
                    .createdAt(json.getString("createdAt"))
                    .scope(json.getString("scope"))
                    .status(json.getString("status"))
                    .build();
        } catch (SnapshotException e) {
            throw e;
        } catch (IOException e) {
            throw new SnapshotException(FlowErrorCode.TF_SNAPSHOT_FAILED, "snapshot 请求失败: " + e.getMessage());
        }
    }

    @Override
    public void restore(String resetEndpointBase, String snapshotId, long timeoutMs) {
        validateBase(resetEndpointBase);
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new SnapshotException(FlowErrorCode.TF_SNAPSHOT_FAILED, "snapshotId 不能为空");
        }
        OkHttpClient client = clientFor(timeoutMs);
        String url = resetEndpointBase + "/restore";
        Map<String, Object> body = Map.of("snapshotId", snapshotId);

        Request httpRequest = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON.toJSONString(body), JSON_MEDIA))
                .build();

        try (Response response = client.newCall(httpRequest).execute()) {
            String responseBody = readBody(response);
            if (!response.isSuccessful()) {
                throw parseRemoteError(FlowErrorCode.TF_SNAPSHOT_RESTORE_FAILED, response.code(), responseBody);
            }
        } catch (SnapshotException e) {
            throw e;
        } catch (IOException e) {
            throw new SnapshotException(FlowErrorCode.TF_SNAPSHOT_RESTORE_FAILED, "restore 请求失败: " + e.getMessage());
        }
    }

    /**
     * 校验快照服务根地址非空，并做 HTTP 出站策略检查（协议/主机；白名单开启时再核对 host:port）。
     *
     * @param base reset 端点根 URL，例如 {@code http://host:port/test-support}
     */
    private void validateBase(String base) {
        if (base == null || base.isBlank()) {
            throw new SnapshotException(FlowErrorCode.TF_SNAPSHOT_ENDPOINT, "reset 端点未配置或 envUrl 无效");
        }
        String policyError = httpEgressAllowlist.check(base);
        if (policyError != null) {
            throw new SnapshotException(FlowErrorCode.TF_SNAPSHOT_ENDPOINT, policyError);
        }
    }

    /** 按请求超时创建客户端；超时为默认 30 秒时复用共享实例 */
    private OkHttpClient clientFor(long timeoutMs) {
        long ms = timeoutMs > 0 ? timeoutMs : 30_000L;
        if (ms == 30_000L) {
            return httpClient;
        }
        return httpClient.newBuilder()
                .connectTimeout(ms, TimeUnit.MILLISECONDS)
                .readTimeout(ms, TimeUnit.MILLISECONDS)
                .writeTimeout(ms, TimeUnit.MILLISECONDS)
                .build();
    }

    private static String readBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body != null ? body.string() : "";
    }

    /** 优先解析被测方 { code, message } 错误体 */
    private static SnapshotException parseRemoteError(FlowErrorCode code, int httpStatus, String body) {
        try {
            JSONObject json = JSON.parseObject(body);
            if (json != null) {
                String remoteCode = json.getString("code");
                String message = json.getString("message");
                if (message != null && !message.isBlank()) {
                    return new SnapshotException(code, remoteCode, message);
                }
            }
        } catch (Exception ignored) {
            // 非 JSON 错误体，走下方兜底
        }
        return new SnapshotException(code, "HTTP " + httpStatus + (body != null && !body.isBlank() ? ": " + body : ""));
    }
}
