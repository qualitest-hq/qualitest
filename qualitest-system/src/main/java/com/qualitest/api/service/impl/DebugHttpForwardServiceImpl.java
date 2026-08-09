package com.qualitest.api.service.impl;

import cn.hutool.json.JSONUtil;
import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.params.DebugHttpForwardParams.DebugBodySpec;
import com.qualitest.api.params.DebugHttpForwardParams.HeaderPair;
import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.api.util.AuthHeaderResolver;
import com.qualitest.api.util.DebugForwardUrlPolicy;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通过 Java HttpClient 将调试请求转发至被测 URL。
 * 若请求带 testProjectApiId，转发前按项目鉴权配置补齐缺失的鉴权头。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DebugHttpForwardServiceImpl implements IDebugHttpForwardService {

    private static final int DEFAULT_TIMEOUT_MS = 60_000;
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "transfer-encoding", "host", "content-length",
            "keep-alive", "proxy-connection", "te", "trailer", "upgrade"
    );

    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;

    @Override
    public DebugHttpForwardResult forward(DebugHttpForwardParams params) {
        if (params == null) {
            return DebugHttpForwardResult.policyError("请求体不能为空");
        }
        injectAuthHeadersIfNeeded(params);
        String policyError = DebugForwardUrlPolicy.validateTargetUrl(params.getUrl());
        if (policyError != null) {
            return DebugHttpForwardResult.policyError(policyError);
        }

        String method = params.getMethod() != null
                ? params.getMethod().trim().toUpperCase(Locale.ROOT)
                : "GET";
        int timeoutMs = params.getTimeoutMs() != null && params.getTimeoutMs() > 0
                ? params.getTimeoutMs()
                : DEFAULT_TIMEOUT_MS;
        boolean followRedirects = params.getFollowRedirects() == null || params.getFollowRedirects();
        boolean allowInsecureTls = Boolean.TRUE.equals(params.getAllowInsecureTls());

        try {
            HttpClient client = buildClient(timeoutMs, followRedirects, allowInsecureTls);
            HttpRequest request = buildRequest(params, method);
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return buildSuccessResult(response);
        } catch (java.net.http.HttpTimeoutException e) {
            log.debug("debug forward timeout correlationId={}", params.getCorrelationId(), e);
            return DebugHttpForwardResult.forwardError(e.getMessage(), "TIMEOUT", null);
        } catch (javax.net.ssl.SSLException e) {
            return DebugHttpForwardResult.forwardError(e.getMessage(), "TLS", null);
        } catch (java.net.UnknownHostException e) {
            return DebugHttpForwardResult.forwardError(e.getMessage(), "DNS", null);
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            String code = classifyIo(msg);
            return DebugHttpForwardResult.forwardError(msg, code, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DebugHttpForwardResult.forwardError("请求已中断", "UNKNOWN", null);
        } catch (IllegalArgumentException e) {
            return DebugHttpForwardResult.policyError(e.getMessage());
        } catch (Exception e) {
            log.warn("debug forward failed correlationId={}", params.getCorrelationId(), e);
            return DebugHttpForwardResult.forwardError(
                    e.getMessage() != null ? e.getMessage() : "转发失败",
                    "UNKNOWN",
                    null);
        }
    }

    /**
     * 调试台带 testProjectApiId 时：缺鉴权头则按项目配置补模板值（已有同名头不覆盖）。
     */
    private void injectAuthHeadersIfNeeded(DebugHttpForwardParams params) {
        Long apiId = params.getTestProjectApiId();
        if (apiId == null) {
            return;
        }
        TestProjectApi api = testProjectApiMapper.selectTestProjectApiById(apiId);
        if (api == null) {
            return;
        }
        String projectAuthJson = null;
        if (api.getTestProjectId() != null) {
            TestProject project = testProjectMapper.selectTestProjectById(api.getTestProjectId());
            if (project != null) {
                projectAuthJson = project.getAuthConfig();
            }
        }
        AuthHeaderResolver.ResolvedAuthHeader resolved = AuthHeaderResolver.resolve(
                api.getAuthConfig(), projectAuthJson, api.getApiPath());
        if (resolved.skipped()) {
            return;
        }

        List<Map<String, Object>> rows = headerPairsToRows(params.getHeaders());
        AuthHeaderResolver.ApplyResult applied = AuthHeaderResolver.applyToHeaderRows(rows, resolved);
        if (applied.changed()) {
            params.setHeaders(rowsToHeaderPairs(applied.headers()));
        }
    }

    private static List<Map<String, Object>> headerPairsToRows(List<HeaderPair> pairs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (pairs == null) {
            return rows;
        }
        for (HeaderPair pair : pairs) {
            if (pair == null || pair.getName() == null || pair.getName().isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("_enabled", true);
            row.put("name", pair.getName());
            row.put("value", pair.getValue() != null ? pair.getValue() : "");
            rows.add(row);
        }
        return rows;
    }

    private static List<HeaderPair> rowsToHeaderPairs(List<Map<String, Object>> rows) {
        List<HeaderPair> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            if (row == null || Boolean.FALSE.equals(row.get("_enabled"))) {
                continue;
            }
            Object nameObj = row.get("name");
            if (nameObj == null || String.valueOf(nameObj).isBlank()) {
                nameObj = row.get("key");
            }
            if (nameObj == null || String.valueOf(nameObj).isBlank()) {
                continue;
            }
            Object value = row.get("value");
            out.add(new HeaderPair(String.valueOf(nameObj).trim(), value != null ? String.valueOf(value) : ""));
        }
        return out;
    }

    private static String classifyIo(String msg) {
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "TIMEOUT";
        }
        if (lower.contains("certificate") || lower.contains("ssl") || lower.contains("tls")) {
            return "TLS";
        }
        if (lower.contains("unknown host") || lower.contains("nodename")) {
            return "DNS";
        }
        return "NETWORK";
    }

    private HttpClient buildClient(int timeoutMs, boolean followRedirects, boolean allowInsecureTls)
            throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(followRedirects ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);
        if (allowInsecureTls) {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, trustAll, new java.security.SecureRandom());
            builder.sslContext(ssl);
        }
        return builder.build();
    }

    private HttpRequest buildRequest(DebugHttpForwardParams params, String method) throws Exception {
        Map<String, String> headers = pairsToMap(params.getHeaders());
        stripHopByHop(headers);

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(params.getUrl().trim()))
                .timeout(Duration.ofMillis(
                        params.getTimeoutMs() != null && params.getTimeoutMs() > 0
                                ? params.getTimeoutMs()
                                : DEFAULT_TIMEOUT_MS));

        BodyPublishResult bodyResult = buildBodyPublisher(params.getBody(), method, headers);
        if (bodyResult.contentType != null && !hasHeader(headers, "content-type")) {
            headers.put("Content-Type", bodyResult.contentType);
        }
        headers.forEach((k, v) -> {
            if (k != null && v != null) {
                req.header(k, v);
            }
        });

        String m = method;
        if ("GET".equals(m) || "HEAD".equals(m)) {
            req.method(m, HttpRequest.BodyPublishers.noBody());
        } else if (bodyResult.publisher != null) {
            req.method(m, bodyResult.publisher);
        } else {
            req.method(m, HttpRequest.BodyPublishers.noBody());
        }
        return req.build();
    }

    private static boolean hasHeader(Map<String, String> headers, String name) {
        return getHeaderIgnoreCase(headers, name, null) != null;
    }

    private static String getHeaderIgnoreCase(Map<String, String> headers, String name, String defaultValue) {
        if (headers == null || name == null) {
            return defaultValue;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue() != null ? e.getValue() : defaultValue;
            }
        }
        return defaultValue;
    }

    private record BodyPublishResult(HttpRequest.BodyPublisher publisher, String contentType) {
    }

    private BodyPublishResult buildBodyPublisher(DebugBodySpec spec, String method, Map<String, String> headers) {
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return new BodyPublishResult(null, null);
        }
        if (spec == null || spec.getKind() == null || "none".equalsIgnoreCase(spec.getKind())) {
            return new BodyPublishResult(null, null);
        }
        String kind = spec.getKind().toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "raw" -> new BodyPublishResult(
                    HttpRequest.BodyPublishers.ofString(spec.getRaw() != null ? spec.getRaw() : ""),
                    getHeaderIgnoreCase(headers, "Content-Type", "text/plain"));
            case "urlencoded" -> new BodyPublishResult(
                    HttpRequest.BodyPublishers.ofString(spec.getRaw() != null ? spec.getRaw() : ""),
                    "application/x-www-form-urlencoded");
            case "json" -> {
                String json = spec.getJson() != null
                        ? JSONUtil.toJsonStr(spec.getJson())
                        : (spec.getRaw() != null ? spec.getRaw() : "{}");
                yield new BodyPublishResult(
                        HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8),
                        "application/json");
            }
            case "formdata" -> buildMultipart(spec);
            case "binary" -> {
                String b64 = spec.getRaw() != null ? spec.getRaw() : "";
                byte[] bytes = b64.isBlank() ? new byte[0] : Base64.getDecoder().decode(b64.trim());
                if (bytes.length > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException(
                            "二进制请求体超过 " + (MAX_FILE_BYTES / 1024 / 1024) + "MB 限制");
                }
                String ct = spec.getContentType() != null && !spec.getContentType().isBlank()
                        ? spec.getContentType()
                        : getHeaderIgnoreCase(headers, "Content-Type", "application/octet-stream");
                yield new BodyPublishResult(HttpRequest.BodyPublishers.ofByteArray(bytes), ct);
            }
            default -> new BodyPublishResult(
                    HttpRequest.BodyPublishers.ofString(spec.getRaw() != null ? spec.getRaw() : ""),
                    null);
        };
    }

    private BodyPublishResult buildMultipart(DebugBodySpec spec) {
        String boundary = "qualitest-" + System.currentTimeMillis();
        var parts = new java.util.ArrayList<byte[]>();

        if (spec.getFields() != null) {
            for (List<String> pair : spec.getFields()) {
                if (pair == null || pair.isEmpty()) {
                    continue;
                }
                String name = pair.get(0);
                String value = pair.size() > 1 ? pair.get(1) : "";
                parts.add(formFieldPart(boundary, name, value != null ? value : ""));
            }
        }
        if (spec.getFiles() != null) {
            for (DebugBodySpec.FormFile f : spec.getFiles()) {
                if (f == null || f.getName() == null) {
                    continue;
                }
                byte[] data = decodeFile(f);
                String fileName = f.getFileName() != null ? f.getFileName() : "file";
                String ct = f.getContentType() != null && !f.getContentType().isBlank()
                        ? f.getContentType()
                        : "application/octet-stream";
                parts.add(filePart(boundary, f.getName(), fileName, ct, data));
            }
        }

        byte[] body = joinMultipart(boundary, parts);
        return new BodyPublishResult(
                HttpRequest.BodyPublishers.ofByteArray(body),
                "multipart/form-data; boundary=" + boundary);
    }

    private byte[] decodeFile(DebugBodySpec.FormFile f) {
        if (f.getBase64() == null || f.getBase64().isBlank()) {
            return new byte[0];
        }
        byte[] data = Base64.getDecoder().decode(f.getBase64().trim());
        if (data.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "文件字段 " + f.getName() + " 超过 " + (MAX_FILE_BYTES / 1024 / 1024) + "MB 限制");
        }
        return data;
    }

    private static byte[] formFieldPart(String boundary, String name, String value) {
        String part = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + escapeQuotes(name) + "\"\r\n\r\n"
                + value + "\r\n";
        return part.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] filePart(String boundary, String name, String fileName, String contentType, byte[] data) {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + escapeQuotes(name)
                + "\"; filename=\"" + escapeQuotes(fileName) + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] tail = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headerBytes.length + data.length + tail.length];
        System.arraycopy(headerBytes, 0, out, 0, headerBytes.length);
        System.arraycopy(data, 0, out, headerBytes.length, data.length);
        System.arraycopy(tail, 0, out, headerBytes.length + data.length, tail.length);
        return out;
    }

    private static String escapeQuotes(String s) {
        return s.replace("\"", "\\\"");
    }

    private static byte[] joinMultipart(String boundary, List<byte[]> parts) {
        byte[] end = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        int len = end.length;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        System.arraycopy(end, 0, out, off, end.length);
        return out;
    }

    private DebugHttpForwardResult buildSuccessResult(HttpResponse<byte[]> response) {
        byte[] bytes = response.body() != null ? response.body() : new byte[0];
        boolean truncated = bytes.length > MAX_RESPONSE_BYTES;
        byte[] slice = truncated ? java.util.Arrays.copyOf(bytes, MAX_RESPONSE_BYTES) : bytes;

        String contentType = response.headers().firstValue("content-type").orElse("");
        String bodyText = decodeBodyPreview(slice, contentType);
        if (truncated) {
            bodyText = bodyText + "\n\n… 响应体已截断（>" + MAX_RESPONSE_BYTES + " 字节）";
        }

        Map<String, String> respHeaders = new LinkedHashMap<>();
        response.headers().map().forEach((k, values) -> {
            if (k == null || isHopByHop(k)) {
                return;
            }
            String v = values != null && !values.isEmpty()
                    ? values.stream().collect(Collectors.joining(", "))
                    : "";
            respHeaders.put(k, v);
        });

        return DebugHttpForwardResult.success(
                response.statusCode(),
                "",
                respHeaders,
                bodyText);
    }

    private static String decodeBodyPreview(byte[] buf, String contentType) {
        String ct = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
        if (ct.contains("json") || ct.contains("text") || ct.contains("xml") || ct.contains("javascript")) {
            return new String(buf, StandardCharsets.UTF_8);
        }
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static Map<String, String> pairsToMap(List<HeaderPair> pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        if (pairs == null) {
            return map;
        }
        for (HeaderPair p : pairs) {
            if (p != null && p.getName() != null && !p.getName().isBlank()) {
                map.put(p.getName(), p.getValue() != null ? p.getValue() : "");
            }
        }
        return map;
    }

    private static void stripHopByHop(Map<String, String> headers) {
        headers.keySet().removeIf(k -> k != null && isHopByHop(k));
    }

    private static boolean isHopByHop(String name) {
        return HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT));
    }
}
