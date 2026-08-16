package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.LoginHint;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 为登录/注册类接口推荐 token 抽取（extract）配置。
 * <p>
 * 优先使用项目鉴权 Profile 中的 loginHint（按端匹配）；
 * 未配置时根据响应 schema 叶路径嗅探 token 字段，再不行用路径兜底 JsonPath。
 */
public final class LoginExtractSuggestor {

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
     * 仅匹配 path 以 /login、/register、/auth/login、/auth/register 结尾的情况。
     */
    public static boolean isLoginLikeApi(String apiPath) {
        String path = ProjectAuthConfigSupport.normalizeApiPath(apiPath).toLowerCase(Locale.ROOT);
        return path.endsWith("/login")
                || path.endsWith("/register")
                || path.endsWith("/auth/login")
                || path.endsWith("/auth/register");
    }

    /**
     * 推荐一条登录 extract；非登录类接口返回 null。
     * <p>
     * 有 loginHint 的 name+expr 时直接采用；否则用 schema 嗅探；再否则用路径兜底表达式。
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
        ProjectAuthConfig projectAuth = ProjectAuthConfigSupport.parse(projectAuthJson);
        String profileId = ProjectAuthConfigSupport.resolveProfileId(apiPath, projectAuth);
        ProjectAuthProfile profile = ProjectAuthConfigSupport.findProfile(projectAuth, profileId);
        String flowKey = ProjectAuthConfigSupport.resolveLoginFlowKey(profile);
        LoginHint hint = profile != null ? profile.getLoginHint() : null;
        String from = ProjectAuthConfigSupport.resolveLoginExtractFrom(hint);
        String expr = ProjectAuthConfigSupport.resolveLoginExtractExpr(hint);

        if (StrUtil.isNotBlank(flowKey) && StrUtil.isNotBlank(expr)) {
            return new Suggestion(
                    flowKey.trim(),
                    StrUtil.blankToDefault(from, "body"),
                    expr.trim());
        }

        String name = StrUtil.isNotBlank(flowKey) ? flowKey.trim() : defaultFlowKeyForPath(apiPath);
        String sniffed = sniffTokenJsonPath(responseSchemaSummary);
        if (sniffed != null) {
            return new Suggestion(name, "body", sniffed);
        }
        return new Suggestion(name, "body", fallbackExprForPath(apiPath));
    }

    /**
     * 无 schema、无 loginHint 时的 JsonPath 兜底：
     * 管理端 auth/login 类用 {@code $.data.token}，其余用 {@code $.token}。
     */
    static String fallbackExprForPath(String apiPath) {
        String path = ProjectAuthConfigSupport.normalizeApiPath(apiPath);
        if (path.contains("/account/auth/login") || path.endsWith("/auth/login")
                || path.contains("/account/auth/register") || path.endsWith("/auth/register")) {
            return "$.data.token";
        }
        return "$.token";
    }

    /**
     * 从响应 schema 叶路径中按优先级找 token 字段，返回带 {@code $.} 前缀的 JsonPath；找不到返回 null。
     */
    static String sniffTokenJsonPath(JSONObject responseSchemaSummary) {
        if (responseSchemaSummary == null || responseSchemaSummary.isEmpty()) {
            return null;
        }
        if (hasPath(responseSchemaSummary, "data.token")) {
            return "$.data.token";
        }
        if (hasPath(responseSchemaSummary, "token")) {
            return "$.token";
        }
        if (hasPath(responseSchemaSummary, "data.accessToken")) {
            return "$.data.accessToken";
        }
        if (hasPath(responseSchemaSummary, "accessToken")) {
            return "$.accessToken";
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
        if (flowKey == null || flowKey.isBlank() || !(rawExtracts instanceof Iterable<?> list)) {
            return false;
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
                return true;
            }
        }
        return false;
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
     * 解析该登录口期望的 flow 变量名：优先 Profile loginFlowKey，否则按路径默认。
     */
    public static String resolveExpectedFlowKey(String projectAuthJson, String apiPath) {
        ProjectAuthConfig projectAuth = ProjectAuthConfigSupport.parse(projectAuthJson);
        String profileId = ProjectAuthConfigSupport.resolveProfileId(apiPath, projectAuth);
        ProjectAuthProfile profile = ProjectAuthConfigSupport.findProfile(projectAuth, profileId);
        String flowKey = ProjectAuthConfigSupport.resolveLoginFlowKey(profile);
        if (StrUtil.isNotBlank(flowKey)) {
            return flowKey.trim();
        }
        return defaultFlowKeyForPath(apiPath);
    }
}
