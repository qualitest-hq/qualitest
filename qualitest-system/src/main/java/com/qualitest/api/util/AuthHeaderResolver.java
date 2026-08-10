package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.Header;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按接口鉴权标签与项目鉴权配置，决定是否补 Authorization 等托管头。
 * <p>
 * 规则：none 不加头；inherit 按路径匹配 Profile 取头模板；
 * 节点已有同名头且未标 profileManaged 时不覆盖（视为人手/AI 显式写入）；
 * 无头或 profileManaged 时按当前项目配置写入/刷新。
 * 造流规范化与 Run/调试发送共用本逻辑。
 */
public final class AuthHeaderResolver {

    /** 节点 headers 行上标记：由项目鉴权托管，Run 时可按最新 Profile 刷新。 */
    public static final String PROFILE_MANAGED = "profileManaged";

    private AuthHeaderResolver() {}

    /**
     * 解析出应使用的鉴权头模板；skipped=true 表示不加/不改鉴权头。
     */
    public static ResolvedAuthHeader resolve(String apiAuthJson, String projectAuthJson, String apiPath) {
        ApiAuthConfig apiAuth = ApiAuthConfigSupport.parseOrInherit(apiAuthJson);
        if (ApiAuthConfig.MODE_NONE.equalsIgnoreCase(StrUtil.trim(apiAuth.getMode()))) {
            return ResolvedAuthHeader.skip();
        }
        if (ApiAuthConfig.MODE_OVERRIDE.equalsIgnoreCase(StrUtil.trim(apiAuth.getMode()))) {
            return resolveOverride(apiAuth);
        }

        ProjectAuthConfig projectAuth = ProjectAuthConfigSupport.parse(projectAuthJson);
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
        if (profile == null || profile.getHeader() == null) {
            return ResolvedAuthHeader.skip();
        }
        Header header = profile.getHeader();
        String name = StrUtil.trimToNull(header.getName());
        String valueTemplate = header.getValueTemplate();
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
     * 基于原 headers 产出新行列表：按需追加或刷新托管头。
     * 造流落盘、Run 发送前、调试 forward 共用此入口。
     *
     * @return 结果含新行列表与是否发生变更（供 warning）
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

    /** 托管行是否已与当前 Profile 模板一致（避免无意义刷新提案）。 */
    private static boolean managedRowMatches(Map<String, Object> row, ResolvedAuthHeader resolved) {
        String name = rowName(row);
        String value = row.get("value") != null ? String.valueOf(row.get("value")).trim() : "";
        return resolved.name().equalsIgnoreCase(name)
                && resolved.valueTemplate().equals(value)
                && !Boolean.FALSE.equals(row.get("_enabled"));
    }

    public static boolean isProfileManaged(Map<String, Object> row) {
        if (row == null) {
            return false;
        }
        Object v = row.get(PROFILE_MANAGED);
        return Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static void fillManagedHeaderRow(Map<String, Object> row, ResolvedAuthHeader resolved) {
        row.put("_enabled", true);
        row.put("name", resolved.name());
        row.put("value", resolved.valueTemplate());
        row.put(PROFILE_MANAGED, true);
    }

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
     * @param skipped       true=不加鉴权头
     * @param name          头名
     * @param valueTemplate 头值模板（可含占位符）
     * @param profileId     命中的 Profile id
     */
    public record ResolvedAuthHeader(boolean skipped, String name, String valueTemplate, String profileId) {
        public static ResolvedAuthHeader skip() {
            return new ResolvedAuthHeader(true, null, null, null);
        }
    }

    /**
     * @param headers 应用后的 headers 行
     * @param changed 是否新增或刷新了托管头
     */
    public record ApplyResult(List<Map<String, Object>> headers, boolean changed) {}
}
