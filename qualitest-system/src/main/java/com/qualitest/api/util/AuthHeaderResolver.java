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
 * none：不加头。
 * inherit：按路径选 Profile，取头名与值模板；pathPrefix 未命中且非单端项目则不加。
 * 节点已有同名头且未标 profileManaged：不覆盖（视为手工头）。
 * 已有另一端 profileManaged 托管头：不覆盖、不追加，防止客户端/管理端凭证串端。
 * 无头或带同端 profileManaged：按当前项目配置写入或刷新，并写入 authProfileId。
 */
public final class AuthHeaderResolver {

    /** 节点 headers 行标记：由项目鉴权托管，Run 时可按最新 Profile 模板刷新。 */
    public static final String PROFILE_MANAGED = "profileManaged";

    /** 托管行绑定的 Profile id；刷新时若与当前解析结果不同端则拒绝覆盖。 */
    public static final String AUTH_PROFILE_ID = "authProfileId";

    private AuthHeaderResolver() {}

    /**
     * 解析应使用的鉴权头。skipped=true 表示不加、不改。method 为空时只按 path 判断免登口。
     */
    public static ResolvedAuthHeader resolve(String apiAuthJson, String projectAuthJson, String apiPath) {
        return resolve(apiAuthJson, projectAuthJson, apiPath, null);
    }

    /**
     * 按 method+path 解析应使用的鉴权头。
     * skipped=true：不加头（none、免登口、无可用 Profile、头模板不完整）。
     * 否则返回头名、值模板、命中的 profileId，以及命中的 pathPrefix（供告警展示）。
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

        // 优先用接口上显式绑定的 authProfileId；否则按 pathPrefix 选端
        String profileId = StrUtil.trimToNull(apiAuth.getAuthProfileId());
        if (profileId == null) {
            profileId = ProjectAuthConfigSupport.resolveProfileId(apiPath, projectAuth);
        }
        if (profileId == null) {
            return ResolvedAuthHeader.skip();
        }
        String matchedPrefix = ProjectAuthConfigSupport.resolveMatchedPathPrefix(apiPath, projectAuth);

        ProjectAuthProfile profile = ProjectAuthConfigSupport.findProfile(projectAuth, profileId);
        String name = ProjectAuthConfigSupport.resolveHeaderName(profile);
        String valueTemplate = ProjectAuthConfigSupport.resolveHeaderValueTemplate(profile);
        if (name == null || StrUtil.isBlank(valueTemplate)) {
            return ResolvedAuthHeader.skip();
        }
        return new ResolvedAuthHeader(false, name, valueTemplate.trim(), profileId, matchedPrefix);
    }

    /** 接口自定义头：读 auth.header；缺字段则不加头。 */
    private static ResolvedAuthHeader resolveOverride(ApiAuthConfig apiAuth) {
        ApiAuthConfig.Header header = apiAuth.getHeader();
        String name = header != null ? StrUtil.trimToNull(header.getName()) : null;
        String valueTemplate = header != null ? StrUtil.trimToNull(header.getValueTemplate()) : null;
        if (name == null || valueTemplate == null) {
            return ResolvedAuthHeader.skip();
        }
        return new ResolvedAuthHeader(false, name, valueTemplate, null, null);
    }

    /**
     * 按解析结果追加或刷新托管头行。
     * 已有另一端托管头（authProfileId 不同）时整表不改动。
     * 头名/值已与模板相同且缺 authProfileId 时：静默补写 id，changed=false，避免反复刷设计警告。
     */
    public static ApplyResult applyToHeaderRows(Object rawHeaders, ResolvedAuthHeader resolved) {
        List<Map<String, Object>> rows = copyRows(rawHeaders);
        if (resolved == null || resolved.skipped()) {
            return new ApplyResult(rows, false);
        }
        if (hasOtherManagedProfile(rows, resolved.profileId())) {
            return new ApplyResult(rows, false);
        }
        Map<String, Object> existing = findHeaderRow(rows, resolved.name());
        if (existing != null) {
            if (!isProfileManaged(existing)) {
                return new ApplyResult(rows, false);
            }
            String rowProfileId = rowAuthProfileId(existing);
            if (rowProfileId != null && resolved.profileId() != null
                    && !rowProfileId.equals(resolved.profileId())) {
                return new ApplyResult(rows, false);
            }
            if (managedRowMatches(existing, resolved)) {
                // 静默补写 authProfileId，不视为变更（避免重复刷 warnings）
                if (resolved.profileId() != null && rowProfileId == null) {
                    existing.put(AUTH_PROFILE_ID, resolved.profileId());
                }
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

    /**
     * 是否已存在另一端的 profileManaged 托管行。
     * 用于阻止「客户端路径却覆盖管理端托管头」这类串端写入。
     */
    private static boolean hasOtherManagedProfile(List<Map<String, Object>> rows, String profileId) {
        if (rows == null || profileId == null) {
            return false;
        }
        for (Map<String, Object> row : rows) {
            if (!isProfileManaged(row)) {
                continue;
            }
            String rowId = rowAuthProfileId(row);
            if (rowId != null && !rowId.equals(profileId)) {
                return true;
            }
        }
        return false;
    }

    /** 读托管行上的 authProfileId，空则 null。 */
    private static String rowAuthProfileId(Map<String, Object> row) {
        if (row == null || row.get(AUTH_PROFILE_ID) == null) {
            return null;
        }
        return StrUtil.trimToNull(String.valueOf(row.get(AUTH_PROFILE_ID)));
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

    /** 把解析结果写入一行：启用、头名、值模板、profileManaged、authProfileId。 */
    private static void fillManagedHeaderRow(Map<String, Object> row, ResolvedAuthHeader resolved) {
        row.put("_enabled", true);
        row.put("name", resolved.name());
        row.put("value", resolved.valueTemplate());
        row.put(PROFILE_MANAGED, true);
        if (resolved.profileId() != null) {
            row.put(AUTH_PROFILE_ID, resolved.profileId());
        }
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
     * 解析结果。
     *
     * @param skipped           true 表示不加鉴权头
     * @param name              头名（如 Authorization）
     * @param valueTemplate     值模板（可含 {{asset.*}} / {{flow.*}}）
     * @param profileId         命中或绑定的 Profile id
     * @param matchedPathPrefix 最长命中的 pathPrefix；显式绑定或未命中前缀时可为 null
     */
    public record ResolvedAuthHeader(
            boolean skipped, String name, String valueTemplate, String profileId, String matchedPathPrefix) {
        /** 表示本节点不加托管鉴权头。 */
        public static ResolvedAuthHeader skip() {
            return new ResolvedAuthHeader(true, null, null, null, null);
        }
    }

    /**
     * 写回头列表。
     *
     * @param headers 可能已追加/刷新的行列表
     * @param changed true 表示新增或刷新了托管头（需记 AUTH_HEADER_MANAGED 提示）
     */
    public record ApplyResult(List<Map<String, Object>> headers, boolean changed) {}
}
