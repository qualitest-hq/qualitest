package com.qualitest.flow.script;

import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.flow.script.tool.ScriptToolMembers;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.qualitest.flow.script.ScriptArgs.requireArgs;

/**
 * 注入 Script 节点脚本的 GraalVM {@code ctx} 宿主对象。
 * <p>
 * <b>变量读写</b>：{@code getFlow}/{@code setFlow}（唯一可写 flow）；{@code getEnv}/{@code getAsset} 只读；
 * {@code getLastHttp} 读上一步 HTTP 快照（status、headers、body、bodyText、responseKind、durationMs）。
 * <p>
 * <b>工具方法</b>：{@code uuid}、{@code log}，以及接口脚本同名可用的 JSON、Base64、URL、XML、
 * 签名原文、摘要、HMAC、RSA、AES、国密 SM2/SM3/SM4 工具函数。
 * <p>
 * <b>外联 HTTP</b>：{@code ctx.http({method, url, headers?, body?, timeoutMs?})}，组装规则与 external HTTP 节点相同，
 * 单步调用次数上限见 {@link ScriptConstants#MAX_HTTP_CALLS_PER_SCRIPT}。
 * <p>
 * <b>Run 会话</b>：{@code ctx.session.get/set}，读写 {@link FlowRunContext#getSession()}（通用 KV，非 HTTP Cookie Jar）。
 * 每次 {@code setFlow} 记录 key/before/after 到 {@link #getWrites()} 供步骤报告展示。
 */
public class ScriptHostContext implements ProxyObject {

    /** ctx 可见成员：宿主自有方法 + 共用工具函数 */
    private static final Set<String> MEMBERS = Stream.concat(
            Stream.of("getFlow", "setFlow", "getEnv", "getAsset", "getLastHttp", "log", "uuid", "http", "session"),
            ScriptToolMembers.names().stream()
    ).collect(Collectors.toUnmodifiableSet());

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
                return ScriptValueConverter.toScriptValue(runContext.getFlow().get(name));
            };
            case "setFlow" -> (ProxyExecutable) args -> {
                requireArgs(args, 2, "setFlow");
                String name = args[0].asString();
                Object converted = ScriptArgs.toJava(args[1]);
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
                return ScriptValueConverter.toScriptValue(runContext.getEnv().get(args[0].asString()));
            };
            case "getAsset" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "getAsset");
                String assetKey = args[0].asString();
                String path = args.length > 1 && !args[1].isNull() ? args[1].asString() : "";
                return ScriptValueConverter.toScriptValue(resolveAsset(assetKey, path));
            };
            // 上一步 HTTP：body 为解析后对象（含 XML 转对象），bodyText 为原文，responseKind 为 json/xml/nonJson
            case "getLastHttp" -> (ProxyExecutable) args -> {
                FlowRunContext.HttpResponseSnapshot last = runContext.getLastResponse();
                if (last == null) {
                    return null;
                }
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("status", last.getStatus());
                snapshot.put("headers", last.getHeaders());
                snapshot.put("body", last.getBody());
                snapshot.put("bodyText", last.getBodyText());
                snapshot.put("responseKind", last.getResponseKind());
                snapshot.put("durationMs", last.getDurationMs());
                return ScriptValueConverter.toScriptValue(snapshot);
            };
            case "log" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "log");
                appendLog(args[0].asString());
                return null;
            };
            case "uuid" -> (ProxyExecutable) args -> UUID.randomUUID().toString();
            case "http" -> (ProxyExecutable) this::http;
            case "session" -> sessionHost;
            // 其余成员按名称取共用工具函数，未注册返回 null
            default -> ScriptToolMembers.get(key);
        };
    }

    private Object http(Value[] args) {
        requireArgs(args, 1, "http");
        if (httpCallCount >= ScriptConstants.MAX_HTTP_CALLS_PER_SCRIPT) {
            throw new IllegalStateException("ctx.http 单步调用次数超过上限 "
                    + ScriptConstants.MAX_HTTP_CALLS_PER_SCRIPT);
        }
        Object raw = ScriptValueConverter.guestToJava(args[0]);
        if (!(raw instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("ctx.http 参数必须是对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) raw;
        httpCallCount++;
        return ScriptValueConverter.toScriptValue(
                ScriptHttpForwarder.forward(runContext, options, forwardService));
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
}
