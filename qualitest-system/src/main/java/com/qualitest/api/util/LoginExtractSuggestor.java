package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig.LoginHint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 为登录/注册类接口推荐 token 抽取（extract）配置。
 * <p>
 * 优先使用项目鉴权预制口 {@code apis[].authConfig.loginHint}（按 method+apiPath）；
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
     * 是否视为登录或注册接口（path 以 /login 或 /register 结尾）。
     * 造流硬拦只认预制口 loginHint，用 hasCredentialLoginHint。
     */
    public static boolean isLoginLikeApi(String apiPath) {
        String path = ProjectAuthConfigSupport.normalizeApiPath(apiPath).toLowerCase(Locale.ROOT);
        return path.endsWith("/login") || path.endsWith("/register");
    }

    /**
     * 优先用预制口 loginHint，没有再按响应 schema 嗅探 token 字段。
     */
    public static Suggestion suggest(
            String projectAuthJson,
            String apiPath,
            JSONObject responseSchemaSummary) {
        return suggest(projectAuthJson, null, apiPath, responseSchemaSummary);
    }

    /**
     * 推荐一条登录 extract。有 loginHint 的 name+expr 时直接采用；否则 schema 嗅探。
     * 不按 URL 猜变量名或 JsonPath。
     */
    public static Suggestion suggest(
            String projectAuthJson,
            String method,
            String apiPath,
            JSONObject responseSchemaSummary) {
        LoginHint hint = ProjectAuthConfigSupport.findLoginHint(
                ProjectAuthConfigSupport.parse(projectAuthJson), method, apiPath);
        String from = ProjectAuthConfigSupport.resolveLoginExtractFrom(hint);
        String expr = ProjectAuthConfigSupport.resolveLoginExtractExpr(hint);
        String flowKey = hint != null ? StrUtil.trimToNull(hint.getFlowKey()) : null;
        if (StrUtil.isNotBlank(flowKey) && StrUtil.isNotBlank(expr)) {
            return new Suggestion(flowKey, StrUtil.blankToDefault(from, "body"), expr.trim());
        }
        String sniffed = sniffTokenJsonPath(responseSchemaSummary);
        if (StrUtil.isNotBlank(flowKey) && sniffed != null) {
            return new Suggestion(flowKey, "body", sniffed);
        }
        if (hint == null && sniffed != null && isLoginLikeApi(apiPath)) {
            return new Suggestion("token", "body", sniffed);
        }
        return null;
    }

    /**
     * 项目预制口是否声明了该 path 的 loginHint（凭证口）。
     */
    public static boolean hasCredentialLoginHint(String projectAuthJson, String method, String apiPath) {
        LoginHint hint = ProjectAuthConfigSupport.findLoginHint(
                ProjectAuthConfigSupport.parse(projectAuthJson), method, apiPath);
        return hint != null && StrUtil.isNotBlank(hint.getFlowKey());
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

    /** schema 叶子里是否有该点分路径（忽略大小写）。 */
    private static boolean hasPath(JSONObject summary, String path) {
        for (String key : summary.keySet()) {
            if (key != null && path.equalsIgnoreCase(key.trim())) {
                return true;
            }
        }
        return false;
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

    /** 在 extracts 列表里找指定 flow 变量名的那一行。 */
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

    /** 把 JsonPath 整理成 $.a.b 形式，便于比较。 */
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
     * 读预制口 loginHint 上的 flow 变量名；没有则返回 null。
     */
    public static String resolveExpectedFlowKey(String projectAuthJson, String apiPath) {
        return resolveExpectedFlowKey(projectAuthJson, null, apiPath);
    }

    /** 带 method 时按 method+path 读 loginHint.flowKey。 */
    public static String resolveExpectedFlowKey(String projectAuthJson, String method, String apiPath) {
        LoginHint hint = ProjectAuthConfigSupport.findLoginHint(
                ProjectAuthConfigSupport.parse(projectAuthJson), method, apiPath);
        return hint != null ? StrUtil.trimToNull(hint.getFlowKey()) : null;
    }
}
