package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按接口鉴权标签和项目配置，决定要不要加托管鉴权头。
 * <p>
 * none 不加头。inherit 按路径选 Profile 取头模板。
 * 节点已有同名头且未标 profileManaged 时不覆盖。
 * 无头或带 profileManaged 时按当前项目配置写入或刷新。
 */
public final class AuthHeaderResolver {

    /** 节点 headers 行上标记：由项目鉴权托管，Run 时可按最新 Profile 刷新。 */
    public static final String PROFILE_MANAGED = "profileManaged";

    private AuthHeaderResolver() {}

    /**
     * 解析应使用的鉴权头。skipped=true 表示不加、不改。method 为空时只按 path 判断免登口。
     */
    public static ResolvedAuthHeader resolve(String apiAuthJson, String projectAuthJson, String apiPath) {
        return resolve(apiAuthJson, projectAuthJson, apiPath, null);
    }

    /**
     * 按 method+path 解析应使用的鉴权头。skipped=true 表示不加、不改。
     */
    public static ResolvedAuthHeader resolve(
            String apiAuthJson, String projectAuthJson, String apiPath, String method) {
        ApiAuthConfig apiAuth = ApiAuthConfigSupport.parseOrInherit(apiAuthJson);
        if (ApiAuthConfig.MODE_NONE.equalsIgnoreCase(StrUtil.trim(apiAuth.getMode()))) {
            return ResolvedAuthHeader.skip();
        }
        if (ApiAuthConfig.MODE_OVERRIDE.equalsIgnoreCase(StrUtil.trim(apiAuth.getMode()))) {
            return resolveOverride(apiAuth);
        }

        ProjectAuthConfig projectAuth = ProjectAuthConfigSupport.parse(projectAuthJson);
        // inherit 且该路径是免登口：不加托管头
        if (ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth(method, apiPath, projectAuth)) {
            return ResolvedAuthHeader.skip();
        }
        if (ProjectAuthConfigSupport.isEmpty(projectAuth)) {
            return ResolvedAuthHeader.skip();
        }

        String profileId = StrUtil.trimToNull(apiAuth.getAuthProfileId());
        if (profileId == null) {
            profileId = ProjectAuthConfigSupport.resolveProfileId(apiPath, projectAuth);
        }
        if (profileId == null) {
            return ResolvedAuthHeader.skip();
        }

        ProjectAuthProfile profile = ProjectAuthConfigSupport.findProfile(projectAuth, profileId);
        String name = ProjectAuthConfigSupport.resolveHeaderName(profile);
        String valueTemplate = ProjectAuthConfigSupport.resolveHeaderValueTemplate(profile);
        if (name == null || StrUtil.isBlank(valueTemplate)) {
            return ResolvedAuthHeader.skip();
        }
        return new ResolvedAuthHeader(false, name, valueTemplate.trim(), profileId);
    }

    /** 接口自定义头：读 auth.header；缺字段则不加头。 */
    private static ResolvedAuthHeader resolveOverride(ApiAuthConfig apiAuth) {
        ApiAuthConfig.Header header = apiAuth.getHeader();
        String name = header != null ? StrUtil.trimToNull(header.getName()) : null;
        String valueTemplate = header != null ? StrUtil.trimToNull(header.getValueTemplate()) : null;
        if (name == null || valueTemplate == null) {
            return ResolvedAuthHeader.skip();
        }
        return new ResolvedAuthHeader(false, name, valueTemplate, null);
    }

    /**
     * 按解析结果追加或刷新托管头行。
     */
    public static ApplyResult applyToHeaderRows(Object rawHeaders, ResolvedAuthHeader resolved) {
        List<Map<String, Object>> rows = copyRows(rawHeaders);
        if (resolved == null || resolved.skipped()) {
            return new ApplyResult(rows, false);
        }
        Map<String, Object> existing = findHeaderRow(rows, resolved.name());
        if (existing != null) {
            if (!isProfileManaged(existing)) {
                return new ApplyResult(rows, false);
            }
            if (managedRowMatches(existing, resolved)) {
                return new ApplyResult(rows, false);
            }
            fillManagedHeaderRow(existing, resolved);
            return new ApplyResult(rows, true);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        fillManagedHeaderRow(row, resolved);
        rows.add(row);
        return new ApplyResult(rows, true);
    }

    /** 托管行的头名和值已等于当前模板，且未禁用。 */
    private static boolean managedRowMatches(Map<String, Object> row, ResolvedAuthHeader resolved) {
        String name = rowName(row);
        String value = row.get("value") != null ? String.valueOf(row.get("value")).trim() : "";
        return resolved.name().equalsIgnoreCase(name)
                && resolved.valueTemplate().equals(value)
                && !Boolean.FALSE.equals(row.get("_enabled"));
    }

    /** 该行是否标了 profileManaged（由项目鉴权托管，可按最新模板刷新）。 */
    public static boolean isProfileManaged(Map<String, Object> row) {
        if (row == null) {
            return false;
        }
        Object v = row.get(PROFILE_MANAGED);
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    /** 把解析结果写入一行：启用、头名、值模板、profileManaged。 */
    private static void fillManagedHeaderRow(Map<String, Object> row, ResolvedAuthHeader resolved) {
        row.put("_enabled", true);
        row.put("name", resolved.name());
        row.put("value", resolved.valueTemplate());
        row.put(PROFILE_MANAGED, true);
    }

    /** 复制 headers 列表，每行拷一份 Map，避免改到原节点数据。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> copyRows(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        }
        return out;
    }

    /** 按头名（忽略大小写）找已启用的那一行。 */
    private static Map<String, Object> findHeaderRow(List<Map<String, Object>> rows, String headerName) {
        if (rows == null || headerName == null) {
            return null;
        }
        for (Map<String, Object> row : rows) {
            if (row == null || Boolean.FALSE.equals(row.get("_enabled"))) {
                continue;
            }
            String name = rowName(row);
            if (headerName.equalsIgnoreCase(name)) {
                return row;
            }
        }
        return null;
    }

    /** 读行上头名：优先 name，没有则用 key。 */
    private static String rowName(Map<String, Object> row) {
        if (row.get("name") != null && !String.valueOf(row.get("name")).isBlank()) {
            return String.valueOf(row.get("name")).trim();
        }
        if (row.get("key") != null) {
            return String.valueOf(row.get("key")).trim();
        }
        return "";
    }

    /**
     * 解析结果。skipped=true 表示不加鉴权头；否则带上头名、值模板和命中的 Profile id。
     */
    public record ResolvedAuthHeader(boolean skipped, String name, String valueTemplate, String profileId) {
        public static ResolvedAuthHeader skip() {
            return new ResolvedAuthHeader(true, null, null, null);
        }
    }

    /**
     * 写回头列表。changed=true 表示新增或刷新了托管头。
     */
    public record ApplyResult(List<Map<String, Object>> headers, boolean changed) {}
}
