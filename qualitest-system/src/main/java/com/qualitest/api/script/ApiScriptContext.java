package com.qualitest.api.script;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API 前置/后置脚本运行时上下文（可变）。
 */
@Getter
@Setter
public class ApiScriptContext {

    private ApiScriptPhase phase;

    private final Map<String, Object> variables = new LinkedHashMap<>();

    private final Map<String, Object> environment = new LinkedHashMap<>();

    private final Map<String, Object> globals = new LinkedHashMap<>();

    private RequestSnapshot request = new RequestSnapshot();

    private ResponseSnapshot response;

    private final List<Map<String, Object>> tests = new ArrayList<>();

    private final List<String> logs = new ArrayList<>();

    private final List<Map<String, Object>> writes = new ArrayList<>();

    private boolean testsFailed;

    public void mergeScopeMaps(Map<String, Object> vars, Map<String, Object> env, Map<String, Object> globs) {
        if (vars != null) {
            variables.clear();
            variables.putAll(copyMap(vars));
        }
        if (env != null) {
            environment.clear();
            environment.putAll(copyMap(env));
        }
        if (globs != null) {
            globals.clear();
            globals.putAll(copyMap(globs));
        }
    }

    public Map<String, Object> exportScopeMaps() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("variables", copyMap(variables));
        out.put("environment", copyMap(environment));
        out.put("globals", copyMap(globals));
        return out;
    }

    public void recordWrite(String scope, String key, Object before, Object after) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("scope", scope);
        record.put("key", key);
        record.put("before", before);
        record.put("value", after);
        writes.add(record);
    }

    public void recordTest(String name, boolean passed, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("passed", passed);
        if (message != null && !message.isBlank()) {
            row.put("message", message);
        }
        tests.add(row);
        if (!passed) {
            testsFailed = true;
        }
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    /** 脚本可见/可变的 HTTP 请求快照。 */
    @Getter
    @Setter
    public static class RequestSnapshot {

        private String method;

        private String url;

        @Getter
        private final Map<String, String> headers = new LinkedHashMap<>();

        private Map<String, Object> body = new LinkedHashMap<>();

        public void setHeadersFromMap(Map<String, String> source) {
            headers.clear();
            if (source != null) {
                headers.putAll(source);
            }
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("method", method);
            map.put("url", url);
            map.put("headers", new LinkedHashMap<>(headers));
            map.put("body", body != null ? new LinkedHashMap<>(body) : Map.of());
            return map;
        }
    }

    /** 脚本可见的 HTTP 响应快照（仅后置脚本）。 */
    @Getter
    @Setter
    public static class ResponseSnapshot {

        private int code;

        private String statusText;

        private final Map<String, Object> headers = new LinkedHashMap<>();

        private String bodyText;

        private Long durationMs;

        public void setHeadersFromMap(Map<?, ?> source) {
            headers.clear();
            if (source == null) {
                return;
            }
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    headers.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("code", code);
            map.put("status", code);
            map.put("statusText", statusText != null ? statusText : "");
            map.put("headers", new LinkedHashMap<>(headers));
            map.put("bodyText", bodyText != null ? bodyText : "");
            map.put("durationMs", durationMs);
            return map;
        }
    }
}
