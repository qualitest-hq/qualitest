package com.qualitest.flow.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.node.StepError;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.run.RunStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HTTP 节点「重试直到通过」配置的解析、规范化与报告辅助。
 * <p>
 * 节点 {@code data.retryUntil} 可含：
 * <ul>
 *   <li>{@code maxAttempts}：本节点最多发出几次 HTTP 请求；不写时按 1（只打一次）</li>
 *   <li>{@code intervalMs}：相邻两次请求之间的等待毫秒；不写时按 1000</li>
 *   <li>{@code until}：可选通过条件数组；每条含 left / operator / right；全部满足才算业务状态到位；
 *       不配则本步成败只由 HTTP 状态校验与业务码校验决定</li>
 * </ul>
 * 运行时：状态码失败、业务码失败、通过条件未满足、请求超时时，若仍有剩余次数则间隔后再请求；
 * 未绑定接口、callMode 非法、外联被拒、脚本错误等不重试。
 */
public final class HttpRetryUntilSupport {

    /** 节点 data 上存放本配置的键名 */
    public static final String DATA_KEY = "retryUntil";

    /** 未配置时的默认最多请求次数（只请求一次） */
    public static final int DEFAULT_MAX_ATTEMPTS = 1;

    /** 允许配置的最多请求次数上限，防止一次节点拖死线程 */
    public static final int MAX_MAX_ATTEMPTS = 60;

    /** 未配置时的默认请求间隔（毫秒） */
    public static final long DEFAULT_INTERVAL_MS = 1_000L;

    /** 允许配置的请求间隔上限（毫秒） */
    public static final long MAX_INTERVAL_MS = 60_000L;

    /**
     * 失败后允许再请求一轮的错误码：
     * HTTP 状态门禁失败、业务码失败、通过条件未满足、请求超时。
     */
    private static final Set<String> RETRYABLE_CODES = Set.of(
            FlowErrorCode.TF_HTTP_STATUS.getCode(),
            FlowErrorCode.TF_BIZ_CODE.getCode(),
            FlowErrorCode.TF_ASSERT_FAILED.getCode(),
            FlowErrorCode.TF_HTTP_TIMEOUT.getCode()
    );

    private HttpRetryUntilSupport() {
    }

    /**
     * 解析后的运行时配置快照。
     *
     * @param maxAttempts 最多请求次数，至少为 1
     * @param intervalMs  两次请求之间的等待毫秒，可为 0
     * @param untilRules  通过条件规则列表；空列表表示未配置通过条件
     */
    public record Resolved(int maxAttempts, long intervalMs, List<JSONObject> untilRules) {

        /** 是否配置了至少一条通过条件 */
        public boolean hasUntil() {
            return untilRules != null && !untilRules.isEmpty();
        }

        /** 是否需要多次请求循环（次数大于 1） */
        public boolean needsLoop() {
            return maxAttempts > 1;
        }
    }

    /**
     * 从节点 data 读取 {@code retryUntil} 并规范化。
     * 键缺失、值为 null 或无法解析为对象时，返回默认：1 次、间隔 1000ms、无通过条件。
     */
    public static Resolved resolve(Map<String, Object> data) {
        Object raw = data != null ? data.get(DATA_KEY) : null;
        if (raw == null) {
            return new Resolved(DEFAULT_MAX_ATTEMPTS, DEFAULT_INTERVAL_MS, List.of());
        }
        JSONObject obj = toObject(raw);
        if (obj == null) {
            return new Resolved(DEFAULT_MAX_ATTEMPTS, DEFAULT_INTERVAL_MS, List.of());
        }
        int maxAttempts = clampAttempts(parseInt(obj.get("maxAttempts"), DEFAULT_MAX_ATTEMPTS));
        long intervalMs = clampInterval(parseLong(obj.get("intervalMs"), DEFAULT_INTERVAL_MS));
        List<JSONObject> until = parseUntilRules(obj.get("until"));
        return new Resolved(maxAttempts, intervalMs, until);
    }

    /**
     * 设计态就地规范化 {@code data.retryUntil}：
     * 脏数字收成合法整数并截断到上下限；
     * 次数为 1 且没有任何通过条件时删除整段键，避免图上残留无意义配置。
     */
    public static void normalizeForDesign(Map<String, Object> data) {
        if (data == null || !data.containsKey(DATA_KEY)) {
            return;
        }
        Object raw = data.get(DATA_KEY);
        if (raw == null) {
            data.remove(DATA_KEY);
            return;
        }
        JSONObject obj = toObject(raw);
        if (obj == null) {
            data.remove(DATA_KEY);
            return;
        }
        int maxAttempts = clampAttempts(parseInt(obj.get("maxAttempts"), DEFAULT_MAX_ATTEMPTS));
        long intervalMs = clampInterval(parseLong(obj.get("intervalMs"), DEFAULT_INTERVAL_MS));
        List<JSONObject> until = parseUntilRules(obj.get("until"));

        if (maxAttempts <= 1 && until.isEmpty()) {
            data.remove(DATA_KEY);
            return;
        }

        Map<String, Object> cleaned = new LinkedHashMap<>();
        cleaned.put("maxAttempts", maxAttempts);
        cleaned.put("intervalMs", intervalMs);
        if (!until.isEmpty()) {
            cleaned.put("until", until);
        }
        data.put(DATA_KEY, cleaned);
    }

    /**
     * 判断本步失败后是否允许再发一次请求。
     * 已通过、无错误码、或不在可重试错误码集合内时返回 false。
     */
    public static boolean isRetryable(StepResult result) {
        if (result == null) {
            return false;
        }
        if (RunStatus.PASSED.getCode().equals(result.getStatus())) {
            return false;
        }
        StepError error = result.getError();
        if (error == null || error.getCode() == null) {
            return false;
        }
        return RETRYABLE_CODES.contains(error.getCode());
    }

    /**
     * 组装写入步骤详情 {@code http.retry} 的摘要：配置上限、间隔、实际次数、每轮摘要列表。
     * 配置了通过条件时额外标记 {@code untilConfigured=true}。
     */
    public static Map<String, Object> buildRetryReport(
            Resolved resolved, int attemptsDone, List<Map<String, Object>> attemptSummaries) {
        Map<String, Object> retry = new LinkedHashMap<>();
        retry.put("maxAttempts", resolved.maxAttempts());
        retry.put("intervalMs", resolved.intervalMs());
        retry.put("attempts", attemptsDone);
        if (resolved.hasUntil()) {
            retry.put("untilConfigured", true);
        }
        if (attemptSummaries != null && !attemptSummaries.isEmpty()) {
            retry.put("attemptSummaries", attemptSummaries);
        }
        return retry;
    }

    /**
     * 生成时间线或报告行尾的次数文案。
     * 实际次数不大于 1 时返回空串；否则返回形如「 · 第 3/10 次」的后缀。
     *
     * @param attempts    实际发出的请求次数
     * @param maxAttempts 配置的次数上限；为 null 时用实际次数代替分母
     */
    public static String formatAttemptSuffix(Object attempts, Object maxAttempts) {
        if (!(attempts instanceof Number n) || n.intValue() <= 1) {
            return "";
        }
        Object max = maxAttempts != null ? maxAttempts : attempts;
        return " · 第 " + attempts + "/" + max + " 次";
    }

    /**
     * 汇总单轮请求结果，供写入 {@code http.retry.attemptSummaries}。
     * 含轮次序号、步骤状态、HTTP 状态码、错误码与消息、本轮通过条件求值结果（若有）。
     */
    public static Map<String, Object> attemptSummary(int attempt, StepResult result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("attempt", attempt);
        if (result == null) {
            return row;
        }
        row.put("status", result.getStatus());
        Map<String, Object> http = result.getHttp();
        if (http != null && http.get("status") != null) {
            row.put("httpStatus", http.get("status"));
        }
        StepError error = result.getError();
        if (error != null) {
            row.put("errorCode", error.getCode());
            if (error.getMessage() != null && !error.getMessage().isBlank()) {
                row.put("errorMessage", error.getMessage());
            }
        }
        if (http != null && http.get("until") != null) {
            row.put("until", http.get("until"));
        }
        return row;
    }

    /** 将次数限制在 1～上限；非法或过小回落到默认 1。 */
    private static int clampAttempts(int n) {
        if (n < 1) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        return Math.min(n, MAX_MAX_ATTEMPTS);
    }

    /** 将间隔限制在 0～上限；负值按 0。 */
    private static long clampInterval(long ms) {
        if (ms < 0) {
            return 0L;
        }
        return Math.min(ms, MAX_INTERVAL_MS);
    }

    /** 把任意对象解析为 int；失败或空则用 defaultValue。 */
    private static int parseInt(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 把任意对象解析为 long；失败或空则用 defaultValue。 */
    private static long parseLong(Object raw, long defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 把 Map / JSON 对象转成 JSONObject；无法转换时返回 null。 */
    private static JSONObject toObject(Object raw) {
        if (raw instanceof JSONObject o) {
            return o;
        }
        if (raw instanceof Map<?, ?> map) {
            return new JSONObject(new LinkedHashMap<>(map));
        }
        try {
            return JSON.parseObject(JSON.toJSONString(raw));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 through 条件数组；跳过 null 项与 left 为空的规则。
     * 非数组或无法解析时返回空列表。
     */
    private static List<JSONObject> parseUntilRules(Object raw) {
        if (raw == null) {
            return List.of();
        }
        JSONArray arr;
        if (raw instanceof JSONArray a) {
            arr = a;
        } else if (raw instanceof List<?> list) {
            arr = JSON.parseArray(JSON.toJSONString(list));
        } else {
            try {
                arr = JSON.parseArray(JSON.toJSONString(raw));
            } catch (Exception e) {
                return List.of();
            }
        }
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject rule = arr.getJSONObject(i);
            if (rule == null) {
                continue;
            }
            String left = rule.getString("left");
            if (left == null || left.isBlank()) {
                continue;
            }
            out.add(rule);
        }
        return List.copyOf(out);
    }
}
