package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * 上游大模型 / 模型列表接口异常的用户可见文案转换。
 * <p>
 * 输入为调用链上抛出的原始异常（可能含英文说明、整段 JSON、脱敏 Key 尾号）；
 * 输出为固定中文提示，或「场景前缀 + 可读细节」。不把原始 JSON 原样返回给前端。
 */
public final class LlmUpstreamErrorMessages {

    /** 鉴权失败（Key 无效、无权访问）。 */
    private static final String MSG_AUTH = "API Key 无效或无权访问，请检查后重试";
    /** 限流或额度不足。 */
    private static final String MSG_RATE_LIMIT = "请求过于频繁或额度不足，请稍后重试";
    /** 连接或读超时。 */
    private static final String MSG_TIMEOUT = "连接模型服务超时，请检查网络或增大读超时";
    /** 模型列表地址不存在（HTTP 404）。 */
    private static final String MSG_NOT_FOUND = "接口地址错误，未找到模型列表端点";
    /** 连接被拒绝（对端未监听或不可达）。 */
    private static final String MSG_CONNECT =
            "无法连接模型服务（连接被拒绝），请检查厂商 Base URL 是否可达、网关是否已启动";
    /** DNS 解析失败。 */
    private static final String MSG_UNKNOWN_HOST = "无法解析模型服务地址，请检查厂商 Base URL";

    private LlmUpstreamErrorMessages() {
    }

    /**
     * 模型发现、测连失败时的文案。
     * 能归类则返回固定中文；否则返回「拉取模型列表失败：细节」；再否则返回通用兜底句。
     *
     * @param error 上游或网络异常
     * @return 面向用户的中文说明
     */
    public static String forDiscovery(Throwable error) {
        return format(error, "拉取模型列表失败", "拉取模型列表失败，请检查接口地址、API Key 与网络");
    }

    /**
     * 对话、Agent 调用失败时的文案。
     * 能归类则返回固定中文；否则返回「模型调用失败：细节」；再否则返回「模型调用失败」。
     *
     * @param error 上游或网络异常
     * @return 面向用户的中文说明
     */
    public static String forChat(Throwable error) {
        return format(error, "模型调用失败", "模型调用失败");
    }

    /**
     * 按 HTTP 状态码映射固定中文说明。
     * 401/403 → 鉴权；404 → 列表端点不存在；408/504 → 超时；429 → 限流；其余返回 null。
     *
     * @param status HTTP 状态码
     * @return 中文说明，无法归类时为 null
     */
    public static String forHttpStatus(int status) {
        return switch (status) {
            case 401, 403 -> MSG_AUTH;
            case 404 -> MSG_NOT_FOUND;
            case 408, 504 -> MSG_TIMEOUT;
            case 429 -> MSG_RATE_LIMIT;
            default -> null;
        };
    }

    /**
     * 组装最终文案：优先用归类结果，其次「前缀：细节」，最后用兜底句。
     *
     * @param error    原始异常
     * @param prefix   无法归类但有可读细节时的前缀
     * @param fallback 既无归类也无细节时的兜底文案
     */
    private static String format(Throwable error, String prefix, String fallback) {
        Resolved resolved = resolve(error);
        if (resolved.mapped() != null) {
            return resolved.mapped();
        }
        if (resolved.detail() != null) {
            return prefix + "：" + resolved.detail();
        }
        return fallback;
    }

    /**
     * 解析异常并决定文案策略（只走一遍异常链与消息文本）。
     * <ol>
     *   <li>按 cause 类型识别连接拒绝、超时、DNS、鉴权类异常名、带 statusCode 的 HTTP 状态</li>
     *   <li>拼接异常链 message，尝试解析其中的 JSON 错误体并按 type/code/message 归类</li>
     *   <li>无法归类时：若有可读 message 则作为细节；纯文本再按关键词兜底；整段无法解析的 JSON 丢弃</li>
     * </ol>
     */
    private static Resolved resolve(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ConnectException) {
                return Resolved.mapped(MSG_CONNECT);
            }
            if (t instanceof SocketTimeoutException) {
                return Resolved.mapped(MSG_TIMEOUT);
            }
            if (t instanceof UnknownHostException) {
                return Resolved.mapped(MSG_UNKNOWN_HOST);
            }
            String className = t.getClass().getName();
            if (className.endsWith("AuthenticationException")
                    || className.contains("Unauthorized")) {
                return Resolved.mapped(MSG_AUTH);
            }
            String byStatus = forHttpStatusOrNull(tryHttpStatus(t));
            if (byStatus != null) {
                return Resolved.mapped(byStatus);
            }
        }

        String blob = collectMessages(error);
        if (blob.isBlank()) {
            return Resolved.empty();
        }

        UpstreamErrorPayload payload = parseUpstreamError(blob);
        if (payload != null) {
            String fromPayload = mapPayload(payload);
            if (fromPayload != null) {
                return Resolved.mapped(fromPayload);
            }
            if (payload.message() != null) {
                return Resolved.detail(truncate(payload.message(), 160));
            }
            return Resolved.empty();
        }

        if (looksLikeAuthFailure(blob)) {
            return Resolved.mapped(MSG_AUTH);
        }
        if (looksLikeRateLimit(blob)) {
            return Resolved.mapped(MSG_RATE_LIMIT);
        }
        String plain = blob.trim();
        if (plain.startsWith("{")) {
            return Resolved.empty();
        }
        return Resolved.detail(truncate(plain, 160));
    }

    /** status 为 null 时返回 null，否则按状态码映射文案。 */
    private static String forHttpStatusOrNull(Integer status) {
        return status == null ? null : forHttpStatus(status);
    }

    /**
     * 根据已解析的错误字段归类：鉴权 → 鉴权文案；限流 → 限流文案；否则返回 null。
     */
    private static String mapPayload(UpstreamErrorPayload payload) {
        if (anyField(payload, LlmUpstreamErrorMessages::looksLikeAuthFailure)) {
            return MSG_AUTH;
        }
        if (anyField(payload, LlmUpstreamErrorMessages::looksLikeRateLimit)) {
            return MSG_RATE_LIMIT;
        }
        return null;
    }

    /** 对 type、code、message 任一字段做匹配，命中即 true。 */
    private static boolean anyField(UpstreamErrorPayload payload, Predicate<String> matcher) {
        return matcher.test(payload.type())
                || matcher.test(payload.code())
                || matcher.test(payload.message());
    }

    /**
     * 从异常文本中截取并解析 JSON 错误体。
     * 支持整段 JSON，也支持前文夹杂状态说明、后跟 JSON 对象的形式。
     * 优先读 error.message / error.type / error.code；没有嵌套 error 时读根上的 message、msg、type、code。
     *
     * @param raw 单条或拼接后的异常 message
     * @return 解析结果；找不到合法对象或字段全空时返回 null
     */
    static UpstreamErrorPayload parseUpstreamError(String raw) {
        String json = extractJsonObject(raw);
        if (json == null) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(json);
            if (root == null || root.isEmpty()) {
                return null;
            }
            String message = null;
            String type = null;
            String code = null;
            Object errorNode = root.get("error");
            if (errorNode instanceof JSONObject errorObj) {
                message = blankToNull(errorObj.getString("message"));
                type = blankToNull(errorObj.getString("type"));
                code = blankToNull(errorObj.getString("code"));
            }
            if (message == null) {
                message = firstNonBlank(root.getString("message"), root.getString("msg"));
            }
            if (type == null) {
                type = blankToNull(root.getString("type"));
            }
            if (code == null) {
                code = blankToNull(root.getString("code"));
            }
            if (message == null && type == null && code == null) {
                return null;
            }
            return new UpstreamErrorPayload(message, type, code);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 从文本中按花括号配对截取第一个 JSON 对象。
     * 从首个「{」起扫描，深度归零时截断；字符串内的括号与转义不计入深度。
     * 未配对成功返回 null。
     *
     * @param raw 可能含前缀的异常文本
     * @return 截取到的 JSON 对象字符串
     */
    static String extractJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int start = raw.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return raw.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * 判断文本是否表示鉴权失败。
     * 匹配常见英文 type/code/message 用语，以及中文「无权访问」「API Key 无效」。
     *
     * @param text type、code、message 或整段异常文本，可为 null
     * @return 疑似鉴权失败时为 true
     */
    static boolean looksLikeAuthFailure(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("authentication_error")
                || lower.contains("authentication fails")
                || lower.contains("invalid_api_key")
                || lower.contains("incorrect api key")
                || lower.contains("invalid api key")
                || (lower.contains("api key") && lower.contains("invalid"))
                || lower.contains("unauthorized")
                || lower.contains("无权访问")
                || lower.contains("api key 无效");
    }

    /**
     * 判断文本是否表示限流或额度问题。
     *
     * @param text type、code、message 或整段异常文本，可为 null
     * @return 疑似限流/额度不足时为 true
     */
    static boolean looksLikeRateLimit(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("rate_limit")
                || lower.contains("too many requests")
                || lower.contains("quota")
                || lower.contains("额度");
    }

    /**
     * 若异常类型提供无参 statusCode() 且返回数字，则取出 HTTP 状态码；否则返回 null。
     */
    private static Integer tryHttpStatus(Throwable t) {
        try {
            var method = t.getClass().getMethod("statusCode");
            Object value = method.invoke(t);
            if (value instanceof Number n) {
                return n.intValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // 无 statusCode 方法时忽略
        }
        return null;
    }

    /**
     * 按 cause 链顺序拼接各层非空 message，空格分隔，供后续 JSON 解析与关键词判断。
     */
    private static String collectMessages(Throwable error) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() == null || t.getMessage().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(t.getMessage().trim());
        }
        return sb.toString();
    }

    /** 返回第一个非空字符串；两者皆空则 null。 */
    private static String firstNonBlank(String first, String second) {
        String a = blankToNull(first);
        return a != null ? a : blankToNull(second);
    }

    /** 空白串视为 null，非空白则 trim 后返回。 */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** 超长细节截断并加省略号，避免把超长上游正文直接推给前端。 */
    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }

    /**
     * 解析结果。
     * mapped：已归类的固定中文，可直接展示；
     * detail：未归类但可读的上游说明，需由调用方加场景前缀。
     */
    private record Resolved(String mapped, String detail) {
        /** 仅固定中文。 */
        static Resolved mapped(String message) {
            return new Resolved(message, null);
        }

        /** 仅可读细节。 */
        static Resolved detail(String detail) {
            return new Resolved(null, detail);
        }

        /** 无可用信息。 */
        static Resolved empty() {
            return new Resolved(null, null);
        }
    }

    /**
     * 上游错误 JSON 中的常用字段。
     *
     * @param message 错误说明（error.message 或根 message/msg）
     * @param type    错误类型（如 authentication_error）
     * @param code    错误码（如 invalid_api_key）
     */
    record UpstreamErrorPayload(String message, String type, String code) {
    }
}
