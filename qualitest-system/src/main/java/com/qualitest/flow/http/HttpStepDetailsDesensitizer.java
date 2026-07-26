package com.qualitest.flow.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * HTTP 步骤报告（{@code step_details.http}）脱敏，主要用于 {@code callMode=external}。
 * <p>
 * 对 request/response 的 headers、body 字段名及 query 参数中匹配敏感键（password、secret、token、client_secret 等）的值替换为 {@code ***}。
 * {@code extracts} 在 http 对象外层，脱敏不作用于 extracts 顶层。
 */
public final class HttpStepDetailsDesensitizer {

    private static final String MASK = "***";

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i)(password|secret|token|api[_-]?key|client[_-]?secret|authorization|access[_-]?token|refresh[_-]?token)"
    );

    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)((?:client_secret|api_key|password|secret|token)=)([^&\\s]+)"
    );

    private HttpStepDetailsDesensitizer() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> desensitize(Map<String, Object> httpDetails) {
        if (httpDetails == null || httpDetails.isEmpty()) {
            return httpDetails;
        }
        Map<String, Object> out = new LinkedHashMap<>(httpDetails);
        Object request = out.get("request");
        if (request instanceof Map<?, ?> reqMap) {
            Map<String, Object> reqCopy = new LinkedHashMap<>((Map<String, Object>) reqMap);
            if (reqCopy.get("headers") instanceof Map<?, ?> headers) {
                reqCopy.put("headers", desensitizeHeaders((Map<String, Object>) headers));
            }
            if (reqCopy.get("body") != null) {
                reqCopy.put("body", desensitizeBody(reqCopy.get("body")));
            }
            out.put("request", reqCopy);
        }
        Object response = out.get("response");
        if (response instanceof Map<?, ?> respMap) {
            Map<String, Object> respCopy = new LinkedHashMap<>((Map<String, Object>) respMap);
            if (respCopy.get("body") != null) {
                respCopy.put("body", desensitizeBodyValue(respCopy.get("body")));
            }
            out.put("response", respCopy);
        }
        Object extracts = out.get("extracts");
        if (extracts instanceof List<?> list) {
            List<Object> masked = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) m);
                    Object name = row.get("name");
                    if (name != null && SENSITIVE_KEY.matcher(String.valueOf(name)).find()) {
                        row.put("value", MASK);
                    }
                    masked.add(row);
                } else {
                    masked.add(item);
                }
            }
            out.put("extracts", masked);
        }
        return out;
    }

    private static Map<String, Object> desensitizeHeaders(Map<String, Object> headers) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : headers.entrySet()) {
            String key = e.getKey();
            if (key != null && SENSITIVE_KEY.matcher(key).find()) {
                out.put(key, MASK);
            } else if ("Authorization".equalsIgnoreCase(key)) {
                out.put(key, MASK);
            } else {
                out.put(key, e.getValue());
            }
        }
        return out;
    }

    private static Object desensitizeBody(Object body) {
        if (body instanceof Map<?, ?> bodyMap) {
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) bodyMap);
            if (copy.get("raw") != null) {
                copy.put("raw", maskSensitiveText(String.valueOf(copy.get("raw"))));
            }
            if (copy.get("fields") instanceof List<?> fields) {
                List<List<String>> maskedFields = new ArrayList<>();
                for (Object row : fields) {
                    if (row instanceof List<?> pair && pair.size() >= 2) {
                        String name = String.valueOf(pair.get(0));
                        String val = String.valueOf(pair.get(1));
                        if (SENSITIVE_KEY.matcher(name).find()) {
                            val = MASK;
                        }
                        maskedFields.add(List.of(name, val));
                    }
                }
                copy.put("fields", maskedFields);
            }
            return copy;
        }
        return desensitizeBodyValue(body);
    }

    private static Object desensitizeBodyValue(Object body) {
        if (body instanceof String s) {
            return maskSensitiveText(s);
        }
        if (body instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object val = e.getValue();
                if (SENSITIVE_KEY.matcher(key).find()) {
                    out.put(key, MASK);
                } else if (val instanceof String str) {
                    out.put(key, maskSensitiveText(str));
                } else {
                    out.put(key, val);
                }
            }
            return out;
        }
        return body;
    }

    static String maskSensitiveText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return SENSITIVE_QUERY.matcher(text).replaceAll("$1" + MASK);
    }
}
