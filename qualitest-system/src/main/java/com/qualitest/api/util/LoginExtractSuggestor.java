package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.LoginHint;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 为登录/注册类接口推荐 token 抽取（extract）配置。
 * <p>
 * 优先使用项目鉴权 Profile 中的 loginHint（按端匹配）；
 * 未配置时根据响应 schema 叶路径嗅探 token 字段。
 * 两者都没有时不编 JsonPath（交给跑流后按真实响应再改）。
 */
public final class LoginExtractSuggestor {

    private static final Set<String> CREDENTIAL_NAMES = Set.of("token", "admintoken", "accesstoken");

    private static final List<String> TOKEN_SCHEMA_PATHS = List.of(
            "data.token",
            "token",
            "data.accessToken",
            "accessToken");

    private static final Set<String> TOKEN_LIKE_EXPR = TOKEN_SCHEMA_PATHS.stream()
            .map(p -> "$." + p.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    /**
     * 一条抽取建议：变量名、来源（默认 body）、JsonPath 表达式。
     */
    public record Suggestion(String name, String from, String expr) {
        /** 转成节点 extracts 行：from / expr / scope=flow / name。 */
        public Map<String, Object> toExtractRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", from != null ? from : "body");
            row.put("expr", expr);
            row.put("scope", "flow");
            row.put("name", name);
            return row;
        }
    }

    private LoginExtractSuggestor() {}

    /**
     * 是否视为登录或注册接口（需要写出 token extract）。
     * path 以 /login 或 /register 结尾即算（含 /auth/login 等）。
     */
    public static boolean isLoginLikeApi(String apiPath) {
        String path = ProjectAuthConfigSupport.normalizeApiPath(apiPath).toLowerCase(Locale.ROOT);
        return path.endsWith("/login") || path.endsWith("/register");
    }

    /**
     * 推荐一条登录 extract；非登录类接口或无法确定 expr 时返回 null。
     * 返回值若非 null，则 name 与 expr 均已填好。
     * <p>
     * 有 loginHint 的 name+expr 时直接采用；否则用 schema 嗅探。不按 URL 编路径。
     *
     * @param projectAuthJson       项目鉴权配置 JSON，可空
     * @param apiPath               接口路径
     * @param responseSchemaSummary 响应 schema 叶路径→类型（无 $ 前缀），可空
     */
    public static Suggestion suggest(
            String projectAuthJson,
            String apiPath,
            JSONObject responseSchemaSummary) {
        if (!isLoginLikeApi(apiPath)) {
            return null;
        }
        LoginSide side = resolveLoginSide(projectAuthJson, apiPath);
        LoginHint hint = side.hint();
        String from = ProjectAuthConfigSupport.resolveLoginExtractFrom(hint);
        String expr = ProjectAuthConfigSupport.resolveLoginExtractExpr(hint);
        if (StrUtil.isNotBlank(side.flowKey()) && StrUtil.isNotBlank(expr)) {
            return new Suggestion(
                    side.flowKey(),
                    StrUtil.blankToDefault(from, "body"),
                    expr.trim());
        }
        String sniffed = sniffTokenJsonPath(responseSchemaSummary);
        if (StrUtil.isNotBlank(side.flowKey()) && sniffed != null) {
            return new Suggestion(side.flowKey(), "body", sniffed);
        }
        return null;
    }

    /**
     * 从响应 schema 叶路径中按优先级找 token 字段，返回带 {@code $.} 前缀的 JsonPath；找不到返回 null。
     */
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

    /**
     * 按路径前缀推断默认 flow 变量名：/api/ 多为 token，管理端路径多为 adminToken。
     */
    static String defaultFlowKeyForPath(String apiPath) {
        String path = ProjectAuthConfigSupport.normalizeApiPath(apiPath);
        if (path.startsWith("/api/")) {
            return "token";
        }
        if (path.startsWith("/system/") || path.startsWith("/monitor/")
                || path.startsWith("/tool/") || path.startsWith("/web/")
                || "/login".equals(path) || "/register".equals(path)) {
            return "adminToken";
        }
        return "token";
    }

    /**
     * 节点 extracts 中是否已存在指定 flow 作用域变量名（scope 为空或 flow 均算）。
     */
    public static boolean extractsContainFlowKey(Object rawExtracts, String flowKey) {
        return findFlowKeyRow(rawExtracts, flowKey) != null;
    }

    /**
     * 指定 flow 变量名对应 extract 行的 expr；没有该行返回 null。
     */
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
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Object scope = row.get("scope");
            if (scope != null && !String.valueOf(scope).isBlank()
                    && !"flow".equalsIgnoreCase(String.valueOf(scope).trim())) {
                continue;
            }
            Object name = row.get("name");
            if (name != null && want.equals(String.valueOf(name).trim())) {
                return row;
            }
        }
        return null;
    }

    /** extracts 是否已包含任一候选 flow 变量名。 */
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

    /**
     * 是否像登录凭证行：变量名为 token/adminToken/accessToken，且 expr 为常见 token JsonPath。
     */
    public static boolean isCredentialLikeExtract(Map<?, ?> row) {
        if (row == null) {
            return false;
        }
        Object name = row.get("name");
        if (name == null || !CREDENTIAL_NAMES.contains(String.valueOf(name).trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        Object expr = row.get("expr");
        return isTokenLikeExpr(expr != null ? String.valueOf(expr) : null);
    }

    /** 两条 JsonPath 是否视为同一提取路径（忽略大小写与首尾空白）。 */
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
     * 解析该登录口期望的 flow 变量名：优先 Profile loginFlowKey，否则按路径默认。
     */
    public static String resolveExpectedFlowKey(String projectAuthJson, String apiPath) {
        return resolveLoginSide(projectAuthJson, apiPath).flowKey();
    }

    private record LoginSide(String flowKey, LoginHint hint) {}

    private static LoginSide resolveLoginSide(String projectAuthJson, String apiPath) {
        ProjectAuthConfig projectAuth = ProjectAuthConfigSupport.parse(projectAuthJson);
        String profileId = ProjectAuthConfigSupport.resolveProfileId(apiPath, projectAuth);
        ProjectAuthProfile profile = ProjectAuthConfigSupport.findProfile(projectAuth, profileId);
        String flowKey = ProjectAuthConfigSupport.resolveLoginFlowKey(profile);
        if (StrUtil.isBlank(flowKey)) {
            flowKey = defaultFlowKeyForPath(apiPath);
        } else {
            flowKey = flowKey.trim();
        }
        LoginHint hint = profile != null ? profile.getLoginHint() : null;
        return new LoginSide(flowKey, hint);
    }
}
