package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolSupport;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 工具调用轨迹的脱敏、截断与 JSON 组装。
 * <p>
 * 供 Agent 循环在每步工具执行后记录 name、入参、返回、成败与耗时，
 * 最终写入助手消息元数据字段 toolTrace，供事后排障展开查看。
 */
public final class AiToolTraceSupport {

    /** 单个字符串字段最多保留的字符数，超出截断并加省略号 */
    public static final int MAX_FIELD_CHARS = 2048;

    /** 敏感字段打码后的占位文本 */
    public static final String REDACTED = "***";

    /** 需整值打码的 JSON 键名（比较时忽略大小写与连字符） */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "token",
            "authorization",
            "secret",
            "apikey",
            "api_key",
            "accesstoken",
            "access_token",
            "refreshtoken",
            "refresh_token",
            "clientsecret",
            "client_secret");

    /** 匹配 Bearer 令牌整串，用于纯文本打码 */
    private static final Pattern BEARER_VALUE = Pattern.compile("(?i)^\\s*bearer\\s+\\S+");

    private AiToolTraceSupport() {
    }

    /**
     * 本轮工具调用累计器。
     * 按执行顺序追加；条数达到上限后不再追加，并将 truncated 标为 true。
     */
    public static final class Recorder {
        private final JSONArray calls = new JSONArray();
        private boolean truncated;
        private int nextIndex = 1;

        /**
         * 记录一次工具执行：入参与结果先脱敏再截断。
         *
         * @param name          工具名
         * @param argumentsJson 模型传入的原始入参 JSON
         * @param resultJson    工具返回的原始 JSON
         * @param durationMs    执行耗时（毫秒）
         * @param maxCalls      本轮最多保留的调用条数；≤0 表示不限制
         */
        public void record(String name, String argumentsJson, String resultJson, long durationMs, int maxCalls) {
            if (maxCalls > 0 && calls.size() >= maxCalls) {
                truncated = true;
                return;
            }
            JSONObject call = new JSONObject();
            call.put("i", nextIndex++);
            call.put("name", name != null ? name : "");
            call.put("ok", isCallOk(resultJson));
            call.put("ms", Math.max(0L, durationMs));
            call.put("args", sanitizePayload(argumentsJson));
            call.put("result", sanitizePayload(resultJson));
            calls.add(call);
        }

        /**
         * 组装本轮完整轨迹对象。
         * 含实际步数、步数上限、是否因条数截断、以及 calls 列表；无调用时仍返回空 calls，便于看步数。
         *
         * @param stepsUsed 本轮实际消耗的工具轮数
         * @param maxSteps  本轮允许的最大工具轮数
         */
        public JSONObject build(int stepsUsed, int maxSteps) {
            JSONObject trace = new JSONObject();
            trace.put("stepsUsed", stepsUsed);
            trace.put("maxSteps", maxSteps);
            trace.put("truncated", truncated);
            trace.put("calls", calls);
            return trace;
        }

        /** 是否已记录至少一次工具调用 */
        public boolean hasCalls() {
            return !calls.isEmpty();
        }
    }

    /**
     * 判断单次工具返回是否表示成功。
     * 顶层含 error、ok 为 false、或 is_error 为 true 视为失败；无法解析 JSON 时按成功处理。
     */
    public static boolean isCallOk(String resultJson) {
        if (FlowDesignToolSupport.isErrorResult(resultJson)) {
            return false;
        }
        if (resultJson == null || resultJson.isBlank()) {
            return true;
        }
        try {
            JSONObject obj = JSON.parseObject(resultJson);
            if (obj == null) {
                return true;
            }
            if (Boolean.FALSE.equals(obj.getBoolean("ok"))) {
                return false;
            }
            if (Boolean.TRUE.equals(obj.getBoolean("is_error"))) {
                return false;
            }
            return true;
        } catch (Exception ex) {
            return true;
        }
    }

    /**
     * 解析入参或结果字符串：能解析为 JSON 则递归脱敏并截断字符串字段；
     * 非 JSON 则按纯文本截断，并对 Bearer 整串打码。
     */
    public static Object sanitizePayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JSONObject();
        }
        String trimmed = raw.trim();
        try {
            Object parsed = JSON.parse(trimmed);
            return sanitizeValue(parsed);
        } catch (Exception ex) {
            return truncateString(maskBearer(trimmed));
        }
    }

    /** 递归处理 JSON 对象、数组与字符串值 */
    private static Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject obj) {
            JSONObject out = new JSONObject();
            for (String key : obj.keySet()) {
                Object child = obj.get(key);
                if (isSensitiveKey(key)) {
                    out.put(key, REDACTED);
                } else {
                    out.put(key, sanitizeValue(child));
                }
            }
            return out;
        }
        if (value instanceof JSONArray arr) {
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.size(); i++) {
                out.add(sanitizeValue(arr.get(i)));
            }
            return out;
        }
        if (value instanceof String s) {
            return truncateString(maskBearer(s));
        }
        return value;
    }

    /** 键名是否属于口令、令牌、密钥等敏感字段 */
    static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT).replace("-", "");
        return SENSITIVE_KEYS.contains(normalized);
    }

    /** 整串为 Bearer 令牌时替换为 Bearer *** */
    static String maskBearer(String value) {
        if (value == null) {
            return null;
        }
        if (BEARER_VALUE.matcher(value).matches()) {
            return "Bearer " + REDACTED;
        }
        return value;
    }

    /** 超过字段上限则截断并追加省略号 */
    static String truncateString(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_FIELD_CHARS) {
            return value;
        }
        return value.substring(0, MAX_FIELD_CHARS) + "…";
    }
}
