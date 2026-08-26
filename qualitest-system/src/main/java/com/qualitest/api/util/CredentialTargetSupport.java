package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Profile 托管头解析凭证目标（{{asset.*}} / {{flow.*}}），供门禁与造流对齐登录 extracts。
 */
public final class CredentialTargetSupport {

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\{\\{\\s*(asset|flow)\\.([^}]+?)\\s*\\}\\}", Pattern.CASE_INSENSITIVE);

    private CredentialTargetSupport() {}

    /**
     * 一条凭证目标：flow 变量或 asset.entry.field。
     */
    public record CredentialTarget(String scope, String flowKey, String entryKey, String fieldPath) {

        public static CredentialTarget flow(String flowKey) {
            String key = StrUtil.trimToNull(flowKey);
            if (key == null) {
                return null;
            }
            return new CredentialTarget("flow", key, null, null);
        }

        public static CredentialTarget asset(String entryKey, String fieldPath) {
            String entry = StrUtil.trimToNull(entryKey);
            String field = StrUtil.trimToNull(fieldPath);
            if (entry == null || field == null) {
                return null;
            }
            return new CredentialTarget("asset", null, entry, field);
        }

        /** 展示路径，如 asset.adminAuth.token / flow.token。 */
        public String displayPath() {
            if ("asset".equals(scope)) {
                return "asset." + entryKey + "." + fieldPath;
            }
            return "flow." + flowKey;
        }

        /** 碰撞/去重用的稳定键。 */
        public String identityKey() {
            return displayPath().toLowerCase(Locale.ROOT);
        }

        public boolean isAsset() {
            return "asset".equalsIgnoreCase(scope);
        }

        public boolean isFlow() {
            return "flow".equalsIgnoreCase(scope);
        }
    }

    /** 登录流 extract 行：目标 + from/expr。 */
    public record CredentialExtract(CredentialTarget target, String from, String expr) {}

    /** 由凭证目标拼出的托管头名与值模板。 */
    public record ManagedHeaderTemplate(String headerName, String headerValueTemplate) {}

    /**
     * 从 extracts 取第一条可用于凭证的行。
     * 优先 scope=asset（须 entryKey+fieldPath）；其次 scope=flow（须 name）。
     */
    public static CredentialExtract firstCredentialExtract(JSONArray extracts) {
        if (extracts == null || extracts.isEmpty()) {
            return null;
        }
        CredentialExtract flowFallback = null;
        for (int i = 0; i < extracts.size(); i++) {
            JSONObject row = extracts.getJSONObject(i);
            if (row == null) {
                continue;
            }
            String expr = StrUtil.trimToNull(row.getString("expr"));
            if (expr == null) {
                continue;
            }
            String from = StrUtil.blankToDefault(row.getString("from"), "body");
            CredentialTarget target = targetFromExtractRow(row);
            if (target == null) {
                continue;
            }
            if (target.isAsset()) {
                return new CredentialExtract(target, from, expr);
            }
            if (flowFallback == null) {
                flowFallback = new CredentialExtract(target, from, expr);
            }
        }
        return flowFallback;
    }

    /** 按 extract 目标与 from/expr 拼托管 Authorization 或 Cookie 头模板。 */
    public static ManagedHeaderTemplate managedHeaderFor(CredentialTarget target, String from, String expr) {
        if (target == null) {
            return null;
        }
        String fromNorm = StrUtil.blankToDefault(from, "body").trim().toLowerCase(Locale.ROOT);
        String placeholder = "{{" + target.displayPath() + "}}";
        if ("setcookie".equals(fromNorm) || "set_cookie".equals(fromNorm)) {
            String cookieName = StrUtil.blankToDefault(expr, "JSESSIONID");
            return new ManagedHeaderTemplate("Cookie", cookieName + "=" + placeholder);
        }
        return new ManagedHeaderTemplate("Authorization", "Bearer " + placeholder);
    }

    /** 从托管头值模板解析全部凭证占位符（保序去重）。 */
    public static List<CredentialTarget> parseFromHeaderTemplate(String headerValueTemplate) {
        List<CredentialTarget> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (StrUtil.isBlank(headerValueTemplate)) {
            return out;
        }
        Matcher matcher = PLACEHOLDER.matcher(headerValueTemplate);
        while (matcher.find()) {
            String scope = matcher.group(1).trim().toLowerCase(Locale.ROOT);
            String rest = matcher.group(2).trim();
            CredentialTarget target = parseRest(scope, rest);
            if (target == null || !seen.add(target.identityKey())) {
                continue;
            }
            out.add(target);
        }
        return out;
    }

    private static CredentialTarget parseRest(String scope, String rest) {
        if (StrUtil.isBlank(rest)) {
            return null;
        }
        if ("flow".equals(scope)) {
            String flowKey = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
            return CredentialTarget.flow(flowKey);
        }
        int dot = rest.indexOf('.');
        if (dot <= 0 || dot >= rest.length() - 1) {
            return null;
        }
        return CredentialTarget.asset(rest.substring(0, dot), rest.substring(dot + 1));
    }

    /** Profile 托管头上的凭证目标。 */
    public static List<CredentialTarget> targetsOnProfile(ProjectAuthProfile profile) {
        if (profile == null) {
            return List.of();
        }
        return parseFromHeaderTemplate(profile.getHeaderValueTemplate());
    }

    /** 取 Profile 托管头上的第一条凭证目标（门禁主路径）。 */
    public static CredentialTarget primaryTarget(ProjectAuthProfile profile) {
        List<CredentialTarget> targets = targetsOnProfile(profile);
        return targets.isEmpty() ? null : targets.get(0);
    }

    /**
     * Cookie 托管头时，尝试从「名={{…}}」解析 Cookie 名，供 setCookie extract。
     */
    public static String cookieNameFromHeader(String headerName, String headerValueTemplate) {
        if (headerName == null || !"cookie".equalsIgnoreCase(headerName.trim())) {
            return null;
        }
        if (StrUtil.isBlank(headerValueTemplate)) {
            return null;
        }
        int eq = headerValueTemplate.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        return StrUtil.trimToNull(headerValueTemplate.substring(0, eq));
    }

    /** extracts 是否已产出该凭证目标。 */
    public static boolean extractsContainTarget(Object rawExtracts, CredentialTarget target) {
        return findExtractRow(rawExtracts, target) != null;
    }

    /** 指定目标 extract 行的 expr。 */
    public static String extractExprForTarget(Object rawExtracts, CredentialTarget target) {
        Map<?, ?> row = findExtractRow(rawExtracts, target);
        if (row == null) {
            return null;
        }
        Object expr = row.get("expr");
        return expr != null && !String.valueOf(expr).isBlank() ? String.valueOf(expr).trim() : null;
    }

    /** 列出 extracts 中产出的凭证目标（flow + asset）。 */
    public static List<CredentialTarget> listProducedTargets(Object rawExtracts) {
        List<CredentialTarget> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (!(rawExtracts instanceof Iterable<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            CredentialTarget target = targetFromExtractRow(row);
            if (target == null || !seen.add(target.identityKey())) {
                continue;
            }
            out.add(target);
        }
        return out;
    }

    private static Map<?, ?> findExtractRow(Object rawExtracts, CredentialTarget target) {
        if (target == null || !(rawExtracts instanceof Iterable<?> list)) {
            return null;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            CredentialTarget produced = targetFromExtractRow(row);
            if (produced != null && Objects.equals(produced.identityKey(), target.identityKey())) {
                return row;
            }
        }
        return null;
    }

    public static CredentialTarget targetFromExtractRow(Map<?, ?> row) {
        if (row == null) {
            return null;
        }
        String scope = row.get("scope") == null || String.valueOf(row.get("scope")).isBlank()
                ? "flow"
                : String.valueOf(row.get("scope")).trim().toLowerCase(Locale.ROOT);
        if ("asset".equals(scope)) {
            Object entry = row.get("entryKey");
            Object field = row.get("fieldPath");
            if (field == null || String.valueOf(field).isBlank()) {
                field = row.get("name");
            }
            return CredentialTarget.asset(
                    entry != null ? String.valueOf(entry) : null,
                    field != null ? String.valueOf(field) : null);
        }
        if ("flow".equals(scope)) {
            Object name = row.get("name");
            return CredentialTarget.flow(name != null ? String.valueOf(name) : null);
        }
        return null;
    }
}
