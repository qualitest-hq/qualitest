package com.qualitest.api.script;

import com.alibaba.fastjson2.JSON;
import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.api.result.DebugHttpForwardResult;
import com.qualitest.api.service.IDebugHttpForwardService;
import com.qualitest.flow.script.ScriptHostContext;
import com.qualitest.flow.script.ScriptValueConverter;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 注入 API 脚本的 {@code api} 宿主对象（前置/后置脚本共用）。
 * <p>
 * 提供 variables / environment / globals、request（仅前置可写）、response（仅后置可读）、
 * test/expect（仅后置）、sendRequest，以及 jsonParse/jsonStringify、hmacSha256、md5、
 * base64Encode/base64Decode。运行在服务端 GraalJS，无 DOM、无浏览器 btoa/atob。
 * 改 request.body 必须整对象重赋；嵌套字段赋值不会回写到真实请求。
 */
public class ApiScriptHost implements ProxyObject {

    private static final Set<String> MEMBERS = Set.of(
            "variables", "environment", "globals", "request", "response", "info",
            "test", "expect", "sendRequest",
            "jsonParse", "jsonStringify", "hmacSha256", "md5", "base64Encode", "base64Decode"
    );

    private final ApiScriptContext context;
    private final IDebugHttpForwardService forwardService;
    private final ProxyObject variablesHost;
    private final ProxyObject environmentHost;
    private final ProxyObject globalsHost;
    private final ProxyObject requestHost;
    private final ProxyObject responseHost;
    private final ProxyObject infoHost;

    public ApiScriptHost(ApiScriptContext context, IDebugHttpForwardService forwardService) {
        this.context = context;
        this.forwardService = forwardService;
        this.variablesHost = new ScopeHost("variables", context.getVariables(), context);
        this.environmentHost = new ScopeHost("environment", context.getEnvironment(), context);
        this.globalsHost = new ScopeHost("globals", context.getGlobals(), context);
        this.requestHost = new RequestHost(context);
        this.responseHost = new ResponseHost(context);
        this.infoHost = new InfoHost(context);
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "variables" -> variablesHost;
            case "environment" -> environmentHost;
            case "globals" -> globalsHost;
            case "request" -> requestHost;
            case "response" -> responseHost;
            case "info" -> infoHost;
            case "test" -> (ProxyExecutable) this::runTest;
            case "expect" -> (ProxyExecutable) args -> ExpectAssertionHost.create(args);
            case "sendRequest" -> (ProxyExecutable) this::sendRequest;
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
                return ScriptHostContext.hmacSha256(args[0].asString(), args[1].asString());
            };
            case "md5" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "md5");
                return ScriptHostContext.md5(args[0].asString());
            };
            // 编码工具：服务端实现，替代浏览器 btoa/atob
            case "base64Encode" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "base64Encode");
                return ScriptHostContext.base64Encode(args[0].asString());
            };
            case "base64Decode" -> (ProxyExecutable) args -> {
                requireArgs(args, 1, "base64Decode");
                return ScriptHostContext.base64Decode(args[0].asString());
            };
            default -> null;
        };
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
        throw new UnsupportedOperationException("api 不允许动态添加属性: " + key);
    }

    private Object runTest(Value[] args) {
        requireArgs(args, 2, "test");
        String name = args[0].asString();
        if (context.getPhase() == ApiScriptPhase.PRE) {
            throw new IllegalStateException("api.test 仅在后置脚本可用");
        }
        Value fn = args[1];
        try {
            fn.execute();
            context.recordTest(name, true, null);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "断言失败";
            context.recordTest(name, false, message);
        }
        return null;
    }

    private Object sendRequest(Value[] args) {
        requireArgs(args, 1, "sendRequest");
        if (forwardService == null) {
            throw new IllegalStateException("sendRequest 不可用");
        }
        Object raw = args[0].isHostObject() ? args[0].asHostObject() : guestToJava(args[0]);
        if (!(raw instanceof Map<?, ?> opts)) {
            throw new IllegalArgumentException("sendRequest 参数必须是对象");
        }
        DebugHttpForwardParams params = buildForwardParams(opts);
        DebugHttpForwardResult result = forwardService.forward(params);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", result.getStatus() != null ? result.getStatus() : 0);
        response.put("status", result.getStatus());
        response.put("statusText", result.getStatusText());
        response.put("headers", result.getResponseHeaders() != null ? result.getResponseHeaders() : Map.of());
        response.put("body", result.getBodyText() != null ? result.getBodyText() : "");
        if (result.getError() != null) {
            response.put("error", result.getError());
        }
        if (args.length > 1 && args[1].canExecute()) {
            Value callback = args[1];
            Object err = result.getError() != null ? result.getError() : null;
            callback.execute(err, response);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private static DebugHttpForwardParams buildForwardParams(Map<?, ?> opts) {
        String url = stringVal(opts.get("url"));
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("sendRequest.url 不能为空");
        }
        String method = stringVal(opts.get("method"));
        if (method == null || method.isBlank()) {
            method = "GET";
        }
        Map<String, String> headers = new LinkedHashMap<>();
        Object headerObj = opts.get("header");
        if (headerObj == null) {
            headerObj = opts.get("headers");
        }
        if (headerObj instanceof Map<?, ?> headerMap) {
            for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
                headers.put(String.valueOf(entry.getKey()), stringVal(entry.getValue()));
            }
        }
        DebugHttpForwardParams.DebugBodySpec body = null;
        Object bodyObj = opts.get("body");
        if (bodyObj != null) {
            if (bodyObj instanceof Map<?, ?> bodyMap) {
                body = ApiScriptSupport.mapToBodySpec((Map<String, Object>) bodyMap);
            } else {
                body = new DebugHttpForwardParams.DebugBodySpec();
                body.setKind("raw");
                body.setRaw(String.valueOf(bodyObj));
            }
        }
        Integer timeoutMs = null;
        Object timeout = opts.get("timeoutMs");
        if (timeout instanceof Number n) {
            timeoutMs = n.intValue();
        }
        return DebugHttpForwardParams.builder()
                .method(method.trim().toUpperCase(Locale.ROOT))
                .url(url.trim())
                .headers(ApiScriptSupport.toHeaderPairs(headers))
                .timeoutMs(timeoutMs)
                .followRedirects(true)
                .allowInsecureTls(false)
                .body(body)
                .build();
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void requireArgs(Value[] args, int min, String name) {
        if (args == null || args.length < min) {
            throw new IllegalArgumentException(name + " 参数不足");
        }
    }

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

    private static final class ScopeHost implements ProxyObject {
        private static final Set<String> KEYS = Set.of("get", "set", "unset", "has");

        private final String scopeName;
        private final Map<String, Object> store;
        private final ApiScriptContext context;

        private ScopeHost(String scopeName, Map<String, Object> store, ApiScriptContext context) {
            this.scopeName = scopeName;
            this.store = store;
            this.context = context;
        }

        @Override
        public Object getMember(String key) {
            return switch (key) {
                case "get" -> (ProxyExecutable) args -> {
                    requireArgs(args, 1, scopeName + ".get");
                    return ScriptValueConverter.toScriptValue(store.get(args[0].asString()));
                };
                case "set" -> (ProxyExecutable) args -> {
                    requireArgs(args, 2, scopeName + ".set");
                    String name = args[0].asString();
                    Object guestValue = args[1].isHostObject() ? args[1].asHostObject() : guestToJava(args[1]);
                    Object converted = ScriptValueConverter.fromGuest(guestValue);
                    Object before = store.get(name);
                    store.put(name, converted);
                    context.recordWrite(scopeName, name, ScriptValueConverter.toGuest(before),
                            ScriptValueConverter.toGuest(converted));
                    return converted;
                };
                case "unset" -> (ProxyExecutable) args -> {
                    requireArgs(args, 1, scopeName + ".unset");
                    return store.remove(args[0].asString());
                };
                case "has" -> (ProxyExecutable) args -> {
                    requireArgs(args, 1, scopeName + ".has");
                    return store.containsKey(args[0].asString());
                };
                default -> null;
            };
        }

        @Override
        public Object getMemberKeys() {
            return KEYS.toArray(new String[0]);
        }

        @Override
        public boolean hasMember(String key) {
            return KEYS.contains(key);
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException(scopeName + " 不允许动态添加属性");
        }
    }

    private static final class RequestHost implements ProxyObject {
        private static final Set<String> KEYS = Set.of("url", "method", "headers", "body");

        private final ApiScriptContext context;

        private RequestHost(ApiScriptContext context) {
            this.context = context;
        }

        @Override
        public Object getMember(String key) {
            ApiScriptContext.RequestSnapshot request = context.getRequest();
            return switch (key) {
                case "url" -> request.getUrl();
                case "method" -> request.getMethod();
                case "headers" -> new HeadersHost(context, true);
                case "body" -> ScriptValueConverter.toScriptValue(request.getBody());
                default -> null;
            };
        }

        @Override
        public void putMember(String key, Value value) {
            if (context.getPhase() != ApiScriptPhase.PRE) {
                throw new UnsupportedOperationException("后置脚本中 request 只读");
            }
            ApiScriptContext.RequestSnapshot request = context.getRequest();
            switch (key) {
                case "url" -> request.setUrl(value.asString());
                case "method" -> request.setMethod(value.asString());
                case "body" -> {
                    Object guest = value.isHostObject() ? value.asHostObject() : guestToJava(value);
                    if (guest instanceof Map<?, ?> map) {
                        Map<String, Object> body = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            body.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                        request.setBody(body);
                    }
                }
                default -> throw new UnsupportedOperationException("request." + key + " 不可写");
            }
        }

        @Override
        public Object getMemberKeys() {
            return KEYS.toArray(new String[0]);
        }

        @Override
        public boolean hasMember(String key) {
            return KEYS.contains(key);
        }
    }

    private static final class ResponseHost implements ProxyObject {
        private static final Set<String> KEYS = Set.of("code", "status", "statusText", "headers", "text", "json");

        private final ApiScriptContext context;

        private ResponseHost(ApiScriptContext context) {
            this.context = context;
        }

        @Override
        public Object getMember(String key) {
            if (context.getPhase() != ApiScriptPhase.POST) {
                throw new IllegalStateException("api.response 仅在后置脚本可用");
            }
            ApiScriptContext.ResponseSnapshot response = context.getResponse();
            if (response == null) {
                return null;
            }
            return switch (key) {
                case "code", "status" -> response.getCode();
                case "statusText" -> response.getStatusText();
                case "headers" -> ScriptValueConverter.toScriptValue(response.getHeaders());
                case "text" -> (ProxyExecutable) args -> response.getBodyText() != null ? response.getBodyText() : "";
                case "json" -> (ProxyExecutable) args -> parseJsonBody(response.getBodyText());
                default -> null;
            };
        }

        @Override
        public Object getMemberKeys() {
            return KEYS.toArray(new String[0]);
        }

        @Override
        public boolean hasMember(String key) {
            return KEYS.contains(key);
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("response 只读");
        }

        private static Object parseJsonBody(String bodyText) {
            if (bodyText == null || bodyText.isBlank()) {
                return null;
            }
            String trimmed = bodyText.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    return ScriptValueConverter.jsonParse(trimmed);
                } catch (Exception ignored) {
                    return bodyText;
                }
            }
            return bodyText;
        }
    }

    private static final class HeadersHost implements ProxyObject {
        private static final Set<String> KEYS = Set.of("add", "set", "remove", "get", "list");

        private final ApiScriptContext context;
        private final boolean mutable;

        private HeadersHost(ApiScriptContext context, boolean mutable) {
            this.context = context;
            this.mutable = mutable;
        }

        @Override
        public Object getMember(String key) {
            Map<String, String> headers = context.getRequest().getHeaders();
            return switch (key) {
                case "add", "set" -> (ProxyExecutable) args -> {
                    ensureMutable();
                    if (args.length == 1 && args[0].hasMembers()) {
                        Value obj = args[0];
                        String name = obj.hasMember("key") ? obj.getMember("key").asString()
                                : obj.hasMember("name") ? obj.getMember("name").asString() : null;
                        String value = obj.hasMember("value") ? obj.getMember("value").asString() : "";
                        if (name == null || name.isBlank()) {
                            throw new IllegalArgumentException("headers.add 需要 key/name");
                        }
                        headers.put(name, value != null ? value : "");
                        return null;
                    }
                    requireArgs(args, 2, "headers.add");
                    headers.put(args[0].asString(), args[1].asString());
                    return null;
                };
                case "remove" -> (ProxyExecutable) args -> {
                    ensureMutable();
                    requireArgs(args, 1, "headers.remove");
                    headers.remove(args[0].asString());
                    return null;
                };
                case "get" -> (ProxyExecutable) args -> {
                    requireArgs(args, 1, "headers.get");
                    return headers.get(args[0].asString());
                };
                case "list" -> ScriptValueConverter.toScriptValue(new ArrayList<>(headers.entrySet()).stream()
                        .map(e -> Map.of("key", e.getKey(), "value", e.getValue()))
                        .toList());
                default -> null;
            };
        }

        private void ensureMutable() {
            if (!mutable || context.getPhase() != ApiScriptPhase.PRE) {
                throw new UnsupportedOperationException("当前阶段 headers 不可修改");
            }
        }

        @Override
        public Object getMemberKeys() {
            return KEYS.toArray(new String[0]);
        }

        @Override
        public boolean hasMember(String key) {
            return KEYS.contains(key);
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("headers 不允许动态添加属性");
        }
    }

    private static final class InfoHost implements ProxyObject {
        private static final Set<String> KEYS = Set.of("eventName");

        private final ApiScriptContext context;

        private InfoHost(ApiScriptContext context) {
            this.context = context;
        }

        @Override
        public Object getMember(String key) {
            if ("eventName".equals(key)) {
                return context.getPhase().getEventName();
            }
            return null;
        }

        @Override
        public Object getMemberKeys() {
            return KEYS.toArray(new String[0]);
        }

        @Override
        public boolean hasMember(String key) {
            return KEYS.contains(key);
        }

        @Override
        public void putMember(String key, Value value) {
            throw new UnsupportedOperationException("info 只读");
        }
    }

    /** {@code api.expect(actual)} 断言链。 */
    private static final class ExpectAssertionHost implements ProxyObject {

        private static final Set<String> MEMBERS = Set.of("to", "equal", "eql", "include");

        private final Object actual;

        private ExpectAssertionHost(Object actual) {
            this.actual = actual;
        }

        static ExpectAssertionHost create(Value[] args) {
            if (args == null || args.length < 1) {
                throw new IllegalArgumentException("expect 参数不足");
            }
            Object actual = args[0].isHostObject() ? args[0].asHostObject() : guestToJava(args[0]);
            return new ExpectAssertionHost(actual);
        }

        @Override
        public Object getMember(String key) {
            return switch (key) {
                case "to" -> new ExpectToHost(actual);
                case "equal" -> (ProxyExecutable) args -> {
                    requireArgs(args, 1, "equal");
                    assertEqual(actual, expectGuestArg(args[0]));
                    return null;
                };
                case "eql" -> (ProxyExecutable) args -> {
                    requireArgs(args, 1, "eql");
                    assertEqual(actual, expectGuestArg(args[0]));
                    return null;
                };
                case "include" -> (ProxyExecutable) args -> {
                    requireArgs(args, 1, "include");
                    Object expected = expectGuestArg(args[0]);
                    String actualText = actual != null ? String.valueOf(actual) : "";
                    String expectedText = expected != null ? String.valueOf(expected) : "";
                    if (!actualText.contains(expectedText)) {
                        expectFail("expected " + actualText + " to include " + expectedText);
                    }
                    return null;
                };
                default -> null;
            };
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
            throw new UnsupportedOperationException("expect 不允许动态添加属性");
        }

        private static void assertEqual(Object actual, Object expected) {
            if (!java.util.Objects.equals(expectNormalize(actual), expectNormalize(expected))) {
                expectFail("expected " + expectStringify(actual) + " to equal " + expectStringify(expected));
            }
        }

        private static Object expectNormalize(Object value) {
            if (value instanceof Number number) {
                double d = number.doubleValue();
                if (Double.isFinite(d) && d == Math.rint(d)) {
                    return (long) d;
                }
                return d;
            }
            return value;
        }

        private static String expectStringify(Object value) {
            return value == null ? "null" : String.valueOf(value);
        }

        private static void expectFail(String message) {
            throw new AssertionError(message);
        }

        private static Object expectGuestArg(Value value) {
            return value.isHostObject() ? value.asHostObject() : guestToJava(value);
        }

        private static final class ExpectToHost implements ProxyObject {
            private static final Set<String> KEYS = Set.of("equal", "eql", "be", "have", "include");

            private final Object actual;

            private ExpectToHost(Object actual) {
                this.actual = actual;
            }

            @Override
            public Object getMember(String key) {
                return switch (key) {
                    case "equal", "eql" -> (ProxyExecutable) args -> {
                        requireArgs(args, 1, "to.equal");
                        assertEqual(actual, expectGuestArg(args[0]));
                        return null;
                    };
                    case "include" -> (ProxyExecutable) args -> {
                        requireArgs(args, 1, "to.include");
                        Object expected = expectGuestArg(args[0]);
                        String actualText = actual != null ? String.valueOf(actual) : "";
                        String expectedText = expected != null ? String.valueOf(expected) : "";
                        if (!actualText.contains(expectedText)) {
                            expectFail("expected " + actualText + " to include " + expectedText);
                        }
                        return null;
                    };
                    case "be" -> new ExpectBeHost(actual);
                    case "have" -> new ExpectHaveHost(actual);
                    default -> null;
                };
            }

            @Override
            public Object getMemberKeys() {
                return KEYS.toArray(new String[0]);
            }

            @Override
            public boolean hasMember(String key) {
                return KEYS.contains(key);
            }

            @Override
            public void putMember(String key, Value value) {
                throw new UnsupportedOperationException("expect.to 不允许动态添加属性");
            }
        }

        private static final class ExpectBeHost implements ProxyObject {
            private static final Set<String> KEYS = Set.of("a", "above", "below");

            private final Object actual;

            private ExpectBeHost(Object actual) {
                this.actual = actual;
            }

            @Override
            public Object getMember(String key) {
                return switch (key) {
                    case "a" -> (ProxyExecutable) args -> {
                        requireArgs(args, 1, "to.be.a");
                        String type = args[0].asString();
                        if (!matchesType(actual, type)) {
                            expectFail("expected value to be a " + type);
                        }
                        return null;
                    };
                    case "above" -> (ProxyExecutable) args -> {
                        requireArgs(args, 1, "to.be.above");
                        compareNumbers(actual, expectGuestArg(args[0]), true);
                        return null;
                    };
                    case "below" -> (ProxyExecutable) args -> {
                        requireArgs(args, 1, "to.be.below");
                        compareNumbers(actual, expectGuestArg(args[0]), false);
                        return null;
                    };
                    default -> null;
                };
            }

            @Override
            public Object getMemberKeys() {
                return KEYS.toArray(new String[0]);
            }

            @Override
            public boolean hasMember(String key) {
                return KEYS.contains(key);
            }

            @Override
            public void putMember(String key, Value value) {
                throw new UnsupportedOperationException("expect.to.be 不允许动态添加属性");
            }

            private static boolean matchesType(Object actual, String type) {
                if (type == null) {
                    return false;
                }
                return switch (type.toLowerCase()) {
                    case "string" -> actual instanceof String;
                    case "number" -> actual instanceof Number;
                    case "boolean" -> actual instanceof Boolean;
                    case "object" -> actual != null && !(actual instanceof String) && !(actual instanceof Number)
                            && !(actual instanceof Boolean);
                    default -> false;
                };
            }

            private static void compareNumbers(Object actual, Object expected, boolean above) {
                double a = toNumber(actual);
                double b = toNumber(expected);
                if (above && !(a > b)) {
                    expectFail("expected " + a + " to be above " + b);
                }
                if (!above && !(a < b)) {
                    expectFail("expected " + a + " to be below " + b);
                }
            }

            private static double toNumber(Object value) {
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
                return Double.parseDouble(String.valueOf(value));
            }
        }

        private static final class ExpectHaveHost implements ProxyObject {
            private static final Set<String> KEYS = Set.of("status", "property");

            private final Object actual;

            private ExpectHaveHost(Object actual) {
                this.actual = actual;
            }

            @Override
            public Object getMember(String key) {
                return switch (key) {
                    case "status" -> (ProxyExecutable) args -> {
                        requireArgs(args, 1, "to.have.status");
                        Object expected = expectGuestArg(args[0]);
                        int expectedStatus = expected instanceof Number n ? n.intValue()
                                : Integer.parseInt(String.valueOf(expected));
                        int actualStatus = actual instanceof Number n ? n.intValue()
                                : Integer.parseInt(String.valueOf(actual));
                        if (actualStatus != expectedStatus) {
                            expectFail("expected status " + actualStatus + " to equal " + expectedStatus);
                        }
                        return null;
                    };
                    case "property" -> (ProxyExecutable) args -> {
                        requireArgs(args, 1, "to.have.property");
                        if (actual instanceof java.util.Map<?, ?> map) {
                            String prop = args[0].asString();
                            if (!map.containsKey(prop)) {
                                expectFail("expected object to have property " + prop);
                            }
                        } else {
                            expectFail("expected object to have property");
                        }
                        return null;
                    };
                    default -> null;
                };
            }

            @Override
            public Object getMemberKeys() {
                return KEYS.toArray(new String[0]);
            }

            @Override
            public boolean hasMember(String key) {
                return KEYS.contains(key);
            }

            @Override
            public void putMember(String key, Value value) {
                throw new UnsupportedOperationException("expect.to.have 不允许动态添加属性");
            }
        }
    }
}
