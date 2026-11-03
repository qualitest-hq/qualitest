package com.qualitest.flow.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 步骤报告中的敏感信息脱敏（外联调用场景常用）。
 * <p>
 * 对 request/response 的 headers、body 字段名及 query 参数中命中敏感键
 * （password、secret、token、api_key、client_secret、authorization 等）的值替换为 {@code ***}。
 * response.bodyText 按 XML 叶子元素处理：标签名命中敏感键时，元素内容（含 CDATA）替换为 {@code ***}，标签与属性保留。
 * extracts 列表若出现在 http 对象内，对 name 命中敏感键的项屏蔽 value。
 */
public final class HttpStepDetailsDesensitizer {

    private static final String MASK = "***";

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i)(password|secret|token|api[_-]?key|client[_-]?secret|authorization|access[_-]?token|refresh[_-]?token)"
    );

    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)((?:client_secret|api_key|password|secret|token)=)([^&\\s]+)"
    );

    /** XML 叶子元素：标签名、属性、文本或单段 CDATA 内容 */
    private static final Pattern XML_LEAF = Pattern.compile(
            "<([A-Za-z_][\\w.:-]*)(\\s[^<>]*)?>(<!\\[CDATA\\[.*?]]>|[^<]*)</\\1\\s*>",
            Pattern.DOTALL
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
            if (respCopy.get("bodyText") instanceof String bodyText) {
                respCopy.put("bodyText", maskSensitiveText(maskSensitiveXml(bodyText)));
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

    /**
     * 对纯文本中的敏感 query 片段脱敏（如 token=xxx → token=***）。
     *
     * @param text 原文；空白则原样返回
     */
    static String maskSensitiveText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return SENSITIVE_QUERY.matcher(text).replaceAll("$1" + MASK);
    }

    /**
     * 对 XML 原文做标签级脱敏：叶子元素标签名命中敏感键时，内容（含一段 CDATA）换成 {@code ***}。
     * 非敏感标签与嵌套结构保持原文；非法或不完整片段尽量原样保留。
     *
     * @param text XML 原文；空白则原样返回
     * @return 脱敏后的文本
     */
    static String maskSensitiveXml(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher m = XML_LEAF.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String replacement = m.group();
            if (SENSITIVE_KEY.matcher(name).find()) {
                String attrs = m.group(2) != null ? m.group(2) : "";
                replacement = "<" + name + attrs + ">" + MASK + "</" + name + ">";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
