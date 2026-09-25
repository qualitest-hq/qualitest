package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.CredentialTargetSupport.CredentialTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 为登录类接口推荐 token 抽取（extract）配置。
 * <p>
 * 按 pathPrefix 命中 Profile 的托管头占位符确定写入目标；
 * from/expr：Cookie 托管头用 setCookie；否则按响应 schema 嗅探 token 字段。
 * 仅对登录/注册类 path 自动建议，避免业务口误补；空 extracts 不硬拦，由人/AI 后补。
 */
public final class LoginExtractSuggestor {

    private static final Set<String> CREDENTIAL_NAMES = Set.of("token", "admintoken", "accesstoken", "jsessionid");

    private static final List<String> TOKEN_SCHEMA_PATHS = List.of(
            "data.token",
            "token",
            "data.accessToken",
            "accessToken");

    private static final Set<String> TOKEN_LIKE_EXPR = TOKEN_SCHEMA_PATHS.stream()
            .map(p -> "$." + p.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    /**
     * 一条抽取建议：目标作用域、来源、表达式。
     */
    public record Suggestion(
            String name,
            String from,
            String expr,
            String scope,
            String entryKey,
            String fieldPath) {

        public static Suggestion flow(String name, String from, String expr) {
            return new Suggestion(name, from, expr, "flow", null, null);
        }

        public static Suggestion asset(String entryKey, String fieldPath, String from, String expr) {
            return new Suggestion(fieldPath, from, expr, "asset", entryKey, fieldPath);
        }

        /** 转成节点 extracts 行。 */
        public Map<String, Object> toExtractRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", from != null ? from : "body");
            row.put("expr", expr);
            String sc = scope != null ? scope : "flow";
            row.put("scope", sc);
            if ("asset".equalsIgnoreCase(sc)) {
                row.put("entryKey", entryKey);
                row.put("fieldPath", fieldPath);
                row.put("name", name != null ? name : fieldPath);
            } else {
                row.put("name", name);
                row.put("entryKey", "");
                row.put("fieldPath", "");
            }
            return row;
        }

        public CredentialTarget toTarget() {
            if ("asset".equalsIgnoreCase(scope)) {
                return CredentialTarget.asset(entryKey, fieldPath);
            }
            return CredentialTarget.flow(name);
        }
    }

    private LoginExtractSuggestor() {}

    /**
     * 是否视为登录或注册接口（path 以 /login 或 /register 结尾）。
     * 仅作建议启发式，不作硬拦门槛。
     */
    public static boolean isLoginLikeApi(String apiPath) {
        String path = ProjectAuthConfigSupport.normalizeApiPath(apiPath).toLowerCase(Locale.ROOT);
        return path.endsWith("/login") || path.endsWith("/register");
    }

    /**
     * 优先用托管头目标 + schema 嗅探。
     */
    public static Suggestion suggest(
            String projectAuthJson,
            String apiPath,
            JSONObject responseSchemaSummary) {
        return suggest(projectAuthJson, null, apiPath, responseSchemaSummary);
    }

    /**
     * 推荐一条登录 extract。登录类 path 上按 pathPrefix Profile 托管头目标写；
     * expr 来自 Cookie 名或 schema。业务口不自动建议。
     */
    public static Suggestion suggest(
            String projectAuthJson,
            String method,
            String apiPath,
            JSONObject responseSchemaSummary) {
        if (!isLoginLikeApi(apiPath)) {
            return null;
        }
        ProjectAuthProfile profile = ProjectAuthConfigSupport.resolveProfile(
                apiPath, ProjectAuthConfigSupport.parse(projectAuthJson));
        CredentialTarget target = profile != null ? CredentialTargetSupport.primaryTarget(profile) : null;
        String cookieName = profile != null
                ? CredentialTargetSupport.cookieNameFromHeader(
                        profile.getHeaderName(), profile.getHeaderValueTemplate())
                : null;

        if (target != null && cookieName != null) {
            return toSuggestion(target, "setCookie", cookieName);
        }

        String sniffed = sniffTokenJsonPath(responseSchemaSummary);
        if (target != null && sniffed != null) {
            return toSuggestion(target, "body", sniffed);
        }
        if (target != null && target.isFlow() && sniffed == null) {
            return null;
        }
        if (target == null && sniffed != null) {
            return Suggestion.flow("token", "body", sniffed);
        }
        return null;
    }

    private static Suggestion toSuggestion(CredentialTarget target, String from, String expr) {
        if (target == null || StrUtil.isBlank(expr)) {
            return null;
        }
        if (target.isAsset()) {
            return Suggestion.asset(target.entryKey(), target.fieldPath(), from, expr);
        }
        return Suggestion.flow(target.flowKey(), from, expr);
    }

    /** 从响应 schema 叶路径中按优先级找 token 字段，返回带 $. 前缀的 JsonPath；找不到返回 null。 */
    static String sniffTokenJsonPath(JSONObject responseSchemaSummary) {
        if (responseSchemaSummary == null || responseSchemaSummary.isEmpty()) {
            return null;
        }
        for (String path : TOKEN_SCHEMA_PATHS) {
            if (hasPath(responseSchemaSummary, path)) {
                return "$." + path;
            }
        }
        return null;
    }

    private static boolean hasPath(JSONObject summary, String path) {
        for (String key : summary.keySet()) {
            if (key != null && path.equalsIgnoreCase(key.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 节点 extracts 中是否已存在指定 flow 作用域变量名。 */
    public static boolean extractsContainFlowKey(Object rawExtracts, String flowKey) {
        return findFlowKeyRow(rawExtracts, flowKey) != null;
    }

    public static String extractExprForFlowKey(Object rawExtracts, String flowKey) {
        Map<?, ?> row = findFlowKeyRow(rawExtracts, flowKey);
        if (row == null) {
            return null;
        }
        Object expr = row.get("expr");
        return expr != null && !String.valueOf(expr).isBlank() ? String.valueOf(expr).trim() : null;
    }

    private static Map<?, ?> findFlowKeyRow(Object rawExtracts, String flowKey) {
        if (flowKey == null || flowKey.isBlank() || !(rawExtracts instanceof Iterable<?> list)) {
            return null;
        }
        String want = flowKey.trim();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row) || !isFlowScopeRow(row)) {
                continue;
            }
            Object name = row.get("name");
            if (name != null && want.equals(String.valueOf(name).trim())) {
                return row;
            }
        }
        return null;
    }

    private static boolean isFlowScopeRow(Map<?, ?> row) {
        Object scope = row.get("scope");
        return scope == null || String.valueOf(scope).isBlank()
                || "flow".equalsIgnoreCase(String.valueOf(scope).trim());
    }

    public static boolean extractsContainAnyFlowKey(Object rawExtracts, Iterable<String> flowKeys) {
        if (flowKeys == null) {
            return false;
        }
        for (String key : flowKeys) {
            if (extractsContainFlowKey(rawExtracts, key)) {
                return true;
            }
        }
        return false;
    }

    /** 是否像登录凭证行：token 类名，或 asset 写入 token/jsessionId 字段。 */
    public static boolean isCredentialLikeExtract(Map<?, ?> row) {
        if (row == null) {
            return false;
        }
        Object scope = row.get("scope");
        String scopeText = scope == null || String.valueOf(scope).isBlank()
                ? "flow"
                : String.valueOf(scope).trim().toLowerCase(Locale.ROOT);
        if ("asset".equals(scopeText)) {
            Object field = row.get("fieldPath");
            if (field == null || String.valueOf(field).isBlank()) {
                field = row.get("name");
            }
            return field != null
                    && CREDENTIAL_NAMES.contains(String.valueOf(field).trim().toLowerCase(Locale.ROOT));
        }
        Object name = row.get("name");
        if (name == null || !CREDENTIAL_NAMES.contains(String.valueOf(name).trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        Object expr = row.get("expr");
        return isTokenLikeExpr(expr != null ? String.valueOf(expr) : null)
                || "setCookie".equalsIgnoreCase(String.valueOf(row.get("from")));
    }

    public static boolean exprsMatch(String expected, String actual) {
        String a = normalizeExpr(expected);
        String b = normalizeExpr(actual);
        return !a.isEmpty() && a.equalsIgnoreCase(b);
    }

    private static boolean isTokenLikeExpr(String expr) {
        String normalized = normalizeExpr(expr).toLowerCase(Locale.ROOT);
        return TOKEN_LIKE_EXPR.contains(normalized);
    }

    static String normalizeExpr(String expr) {
        if (expr == null) {
            return "";
        }
        String text = expr.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.startsWith("$.")) {
            return text;
        }
        if (!text.startsWith("$") && !text.contains("{{")) {
            return "$." + text;
        }
        return text;
    }

    /**
     * 该登录类口应对齐的凭证目标（pathPrefix 命中 Profile 的托管头）。
     * 非登录类 path 返回 null。
     */
    public static CredentialTarget resolveExpectedTarget(String projectAuthJson, String method, String apiPath) {
        if (!isLoginLikeApi(apiPath)) {
            return null;
        }
        ProjectAuthProfile profile = ProjectAuthConfigSupport.resolveProfile(
                apiPath, ProjectAuthConfigSupport.parse(projectAuthJson));
        return CredentialTargetSupport.primaryTarget(profile);
    }
}
