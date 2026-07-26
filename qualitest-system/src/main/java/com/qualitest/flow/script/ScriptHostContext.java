package com.qualitest.flow.script;

import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.context.FlowRunContext;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 注入 Script 节点脚本的 GraalVM {@code ctx} 宿主对象。
 * <p>
 * <b>变量读写</b>：{@code getFlow}/{@code setFlow}（唯一可写 flow）；{@code getEnv}/{@code getAsset} 只读；
 * {@code getLastHttp} 读上一步 HTTP 响应摘要。
 * <p>
 * <b>工具方法</b>：{@code jsonParse}/{@code jsonStringify}、{@code hmacSha256}/{@code md5}、
 * {@code base64Encode}/{@code base64Decode}、{@code uuid}、{@code log}。
 * <p>
 * <b>外联 HTTP</b>：{@code ctx.http({method, url, headers?, body?, timeoutMs?})}，组装规则与 external HTTP 节点相同，
 * 单步调用次数上限见 {@link ScriptConstants#MAX_HTTP_CALLS_PER_SCRIPT}。
 * <p>
 * <b>Run 会话</b>：{@code ctx.session.get/set}，与 HTTP {@code useRunSession} 共用 {@link com.qualitest.flow.session.FlowRunSession}。
 * 每次 {@code setFlow} 记录 key/before/after 到 {@link #getWrites()} 供步骤报告展示。
 */
public class ScriptHostContext implements ProxyObject {

    private static final Set<String> MEMBERS = Set.of(
            "getFlow", "setFlow", "getEnv", "getAsset", "getLastHttp",
            "jsonParse", "jsonStringify", "hmacSha256", "md5",
            "log", "base64Encode", "base64Decode", "uuid", "http", "session"
    );

    private final FlowRunContext runContext;
    private final IDebugHttpForwardService forwardService;
    private final ScriptSessionHost sessionHost;
    private final List<Map<String, Object>> writes = new ArrayList<>();
    private final List<String> logs;
    private int httpCallCount;

    public ScriptHostContext(FlowRunContext runContext, List<String> logs) {
        this(runContext, logs, null);
    }

    public ScriptHostContext(
            FlowRunContext runContext,
            List<String> logs,
            IDebugHttpForwardService forwardService
    ) {
        this.runContext = runContext;
        this.logs = logs;
        this.forwardService = forwardService;
        this.sessionHost = new ScriptSessionHost(runContext);
    }

    public List<Map<String, Object>> getWrites() {
        return writes;
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "getFlow" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "getFlow");
                String name = args[0].asString();
                return ScriptValueConverter.toGuest(runContext.getFlow().get(name));
            };
            case "setFlow" -> (ProxyExecutable) args -> {
                requireArgs(args, 2, "setFlow");
                String name = args[0].asString();
                Object guestValue = args[1].isHostObject() ? args[1].asHostObject() : guestToJava(args[1]);
                Object converted = ScriptValueConverter.fromGuest(guestValue);
                Object before = runContext.getFlow().get(name);
                runContext.getFlow().put(name, converted);
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("key", name);
                record.put("before", ScriptValueConverter.toGuest(before));
                record.put("value", ScriptValueConverter.toGuest(converted));
                writes.add(record);
                return converted;
            };
            case "getEnv" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "getEnv");
                return ScriptValueConverter.toGuest(runContext.getEnv().get(args[0].asString()));
            };
            case "getAsset" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "getAsset");
                String assetKey = args[0].asString();
                String path = args.length > 1 && !args[1].isNull() ? args[1].asString() : "";
                return ScriptValueConverter.toGuest(resolveAsset(assetKey, path));
            };
            case "getLastHttp" -> (ProxyExecutable) args -> {
                FlowRunContext.HttpResponseSnapshot last = runContext.getLastResponse();
                if (last == null) {
                    return null;
                }
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("status", last.getStatus());
                snapshot.put("headers", ScriptValueConverter.toGuest(last.getHeaders()));
                snapshot.put("body", ScriptValueConverter.toGuest(last.getBody()));
                snapshot.put("durationMs", last.getDurationMs());
                return snapshot;
            };
            case "jsonParse" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "jsonParse");
                return ScriptValueConverter.jsonParse(args[0].asString());
            };
            case "jsonStringify" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "jsonStringify");
                Object val = args[0].isHostObject() ? args[0].asHostObject() : guestToJava(args[0]);
                return ScriptValueConverter.jsonStringify(val);
            };
            case "hmacSha256" -> (ProxyExecutable) args -> {
                requireArgs(args, 2, "hmacSha256");
                return hmacSha256(args[0].asString(), args[1].asString());
            };
            case "md5" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "md5");
                return md5(args[0].asString());
            };
            case "log" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "log");
                appendLog(args[0].asString());
                return null;
            };
            case "base64Encode" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "base64Encode");
                return base64Encode(args[0].asString());
            };
            case "base64Decode" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "base64Decode");
                return base64Decode(args[0].asString());
            };
            case "uuid" -> (ProxyExecutable) args -> UUID.randomUUID().toString();
            case "http" -> (ProxyExecutable) this::http;
            case "session" -> sessionHost;
            default -> null;
        };
    }

    private Object http(Value[] args) {
        requireArgs(args, 1, "http");
        if (httpCallCount >= ScriptConstants.MAX_HTTP_CALLS_PER_SCRIPT) {
            throw new IllegalStateException("ctx.http 单步调用次数超过上限 "
                    + ScriptConstants.MAX_HTTP_CALLS_PER_SCRIPT);
        }
        Object raw = args[0].isHostObject() ? args[0].asHostObject() : guestToJava(args[0]);
        if (!(raw instanceof Map<?, ?> opts)) {
            throw new IllegalArgumentException("ctx.http 参数必须是对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) raw;
        httpCallCount++;
        return ScriptHttpForwarder.forward(runContext, options, forwardService);
    }

    @Override
    public Object getMemberKeys() {
        return MEMBERS.toArray(new String[0]);
    }

    @Override
    public boolean hasMember(String key) {
        return MEMBERS.contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("ctx 不允许动态添加属性: " + key);
    }

    /** 追加一行脚本标准输出到步骤日志 */
    public void appendLog(String line) {
        if (line != null && !line.isBlank()) {
            logs.add(line);
        }
    }

    private Object resolveAsset(String key, String path) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Object root = runContext.getAsset().get(key.trim());
        if (path == null || path.isBlank()) {
            return root;
        }
        return navigate(root, path.trim());
    }

    private static Object navigate(Object current, String path) {
        if (current == null || path == null || path.isEmpty()) {
            return current;
        }
        Object node = current;
        for (String segment : path.split("\\.")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (node instanceof Map<?, ?> map) {
                node = map.get(segment);
            } else {
                return null;
            }
        }
        return node;
    }

    private static void requireArgs(Value[] args, int min, String name) {
        if (args == null || args.length < min) {
            throw new IllegalArgumentException(name + " 参数不足");
        }
    }

    /** 将 Graal Value 转为 Java 基本结构 */
    static Object guestToJava(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, guestToJava(value.getMember(key)));
            }
            return map;
        }
        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            long size = value.getArraySize();
            for (long i = 0; i < size; i++) {
                list.add(guestToJava(value.getArrayElement(i)));
            }
            return list;
        }
        return value.toString();
    }

    static String base64Encode(String data) {
        return Base64.getEncoder().encodeToString(String.valueOf(data).getBytes(StandardCharsets.UTF_8));
    }

    static String base64Decode(String data) {
        return new String(Base64.getDecoder().decode(String.valueOf(data)), StandardCharsets.UTF_8);
    }

    /** 计算 HMAC-SHA256，返回小写十六进制字符串 */
    static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(String.valueOf(secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(String.valueOf(data).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("hmacSha256 失败: " + e.getMessage(), e);
        }
    }

    /** 计算 MD5，返回小写十六进制字符串 */
    static String md5(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(String.valueOf(data).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("md5 失败: " + e.getMessage(), e);
        }
    }
}
