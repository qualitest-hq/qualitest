package com.qualitest.project.auth;

import cn.hutool.core.util.StrUtil;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.flow.context.MustacheScan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 托管鉴权头：画布设计期只剥离节点上误落的 profileManaged 行，开跑时才由请求构建补头。
 * <p>
 * 开跑补头入口在 {@code FlowHttpRequestBuilder}，经 {@link AuthHeaderResolver} 按接口 auth.mode 决定：
 * none 不加、inherit 用 Profile、override 用接口头。不根据预制接口列表或节点 extracts 决定是否跳过补头。
 */
public final class ManagedAuthHeaderApplier {

    private ManagedAuthHeaderApplier() {}

    /**
     * 设计期：去掉节点上的 profileManaged 托管行，不写入新托管头。
     *
     * @return 是否删除了托管行
     */
    public static boolean stripManagedFromNodeData(Map<String, Object> data) {
        return stripManagedAuthHeaders(data);
    }

    /**
     * 去掉 profileManaged 托管行。
     *
     * @return 是否删除了托管行
     */
    public static boolean stripManagedAuthHeaders(Map<String, Object> data) {
        if (data == null) {
            return false;
        }
        Object raw = data.get("headers");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean removed = false;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) m;
            if (AuthHeaderResolver.isProfileManaged(row)) {
                removed = true;
                continue;
            }
            rows.add(row);
        }
        if (!removed) {
            return false;
        }
        data.put("headers", rows);
        return true;
    }

    /** 模板中的通用占位路径（小写），来自 {@link MustacheScan}。 */
    public static Set<String> placeholderPathsInTemplate(String headerValueTemplate) {
        Set<String> out = new LinkedHashSet<>();
        if (StrUtil.isBlank(headerValueTemplate)) {
            return out;
        }
        for (String inner : MustacheScan.listInners(headerValueTemplate)) {
            if (StrUtil.isNotBlank(inner)) {
                out.add(inner.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** 项目各 Profile 托管头模板里的占位路径。 */
    public static Set<String> placeholderPathsInProjectAuth(ProjectAuthConfig config) {
        Set<String> out = new LinkedHashSet<>();
        if (config == null || config.getAuthProfiles() == null) {
            return out;
        }
        for (ProjectAuthProfile profile : config.getAuthProfiles()) {
            if (profile == null) {
                continue;
            }
            out.addAll(placeholderPathsInTemplate(profile.getHeaderValueTemplate()));
        }
        return out;
    }

    /**
     * extracts 是否写出 templates 中任一占位路径。
     * 只认明确的 scope / entryKey / name，不做启发式拆分。
     */
    public static boolean extractsWriteAnyOf(Object rawExtracts, Set<String> templatePaths) {
        if (templatePaths == null || templatePaths.isEmpty() || !(rawExtracts instanceof Iterable<?> list)) {
            return false;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            String path = pathWrittenByExtractRow(row);
            if (path != null && templatePaths.contains(path)) {
                return true;
            }
        }
        return false;
    }

    /** 单行 extract 将写入的占位路径（小写）；无法解析则 null。 */
    static String pathWrittenByExtractRow(Map<?, ?> row) {
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
            String entryKey = entry != null ? String.valueOf(entry).trim() : "";
            String fieldPath = field != null ? String.valueOf(field).trim() : "";
            if (entryKey.isEmpty() || fieldPath.isEmpty()) {
                return null;
            }
            return ("asset." + entryKey + "." + fieldPath).toLowerCase(Locale.ROOT);
        }
        if ("flow".equals(scope) || "env".equals(scope)) {
            Object name = row.get("name");
            if (name == null || String.valueOf(name).isBlank()) {
                return null;
            }
            return (scope + "." + String.valueOf(name).trim()).toLowerCase(Locale.ROOT);
        }
        return null;
    }
}
