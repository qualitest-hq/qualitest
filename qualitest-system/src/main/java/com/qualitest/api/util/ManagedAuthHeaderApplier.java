package com.qualitest.api.util;

import cn.hutool.core.util.StrUtil;
import com.qualitest.api.model.ApiAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.AuthHeaderResolver.ResolvedAuthHeader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将项目鉴权托管头写入 HTTP 节点 {@code data.headers}（造流 Normalizer / 批量刷新共用）。
 * <p>
 * 登录口 / 免登 path / 本节点正抽取 token 时：不补 Authorization，并剥离已有托管头。
 * 「是否免登」与 {@link AuthHeaderResolver#resolve} 共用
 * {@link ProjectAuthConfigSupport#shouldTreatAsAnonymousAuth}，本类额外负责剥离与 token 生产者短路。
 */
public final class ManagedAuthHeaderApplier {

    private ManagedAuthHeaderApplier() {}

    /**
     * 按接口鉴权与项目配置补齐或刷新托管头。
     *
     * @return 是否发生变更（新增、刷新或剥离了托管行）
     */
    public static boolean applyToNodeData(
            Map<String, Object> data,
            String apiAuthJson,
            String projectAuthJson,
            String apiPath,
            String nodeLabel,
            List<String> warnings) {
        if (data == null) {
            return false;
        }

        ProjectAuthConfig projectAuth = ProjectAuthConfigSupport.parse(projectAuthJson);
        // token 生产者 resolve 不一定 skip（path 非免登），须先短路
        if (producesLoginFlowKey(data, projectAuth)) {
            return stripManagedAuthHeaders(data, nodeLabel, warnings);
        }

        ResolvedAuthHeader resolved = AuthHeaderResolver.resolve(apiAuthJson, projectAuthJson, apiPath);
        if (resolved == null || resolved.skipped()) {
            // resolve 已对 mode=none / 免登 path skip；若仍有误补托管头则剥掉
            ApiAuthConfig apiAuth = ApiAuthConfigSupport.parseOrInherit(apiAuthJson);
            boolean anonOrNone = ApiAuthConfig.MODE_NONE.equalsIgnoreCase(StrUtil.trim(apiAuth.getMode()))
                    || ProjectAuthConfigSupport.shouldTreatAsAnonymousAuth(apiPath, projectAuth);
            return anonOrNone && stripManagedAuthHeaders(data, nodeLabel, warnings);
        }

        AuthHeaderResolver.ApplyResult applied = AuthHeaderResolver.applyToHeaderRows(data.get("headers"), resolved);
        data.put("headers", applied.headers());
        if (applied.changed() && warnings != null) {
            warnings.add(AuthDesignWarningCodes.headerManaged(
                    nodeLabel,
                    resolved.name() != null ? resolved.name() : "Authorization"));
        }
        return applied.changed();
    }

    /**
     * 本节点 extracts 是否写入项目 loginHint 中的任一 flowKey（如 token / adminToken）。
     */
    static boolean producesLoginFlowKey(Map<String, Object> data, ProjectAuthConfig projectAuth) {
        Set<String> loginKeys = collectLoginFlowKeys(projectAuth);
        if (loginKeys.isEmpty()) {
            loginKeys = Set.of("token", "adminToken");
        }
        return LoginExtractSuggestor.extractsContainAnyFlowKey(
                data != null ? data.get("extracts") : null, loginKeys);
    }

    private static Set<String> collectLoginFlowKeys(ProjectAuthConfig projectAuth) {
        Set<String> keys = new LinkedHashSet<>();
        if (projectAuth == null || projectAuth.getAuthProfiles() == null) {
            return keys;
        }
        for (ProjectAuthProfile profile : projectAuth.getAuthProfiles()) {
            if (profile == null) {
                continue;
            }
            String flowKey = ProjectAuthConfigSupport.resolveLoginFlowKey(profile);
            if (StrUtil.isNotBlank(flowKey)) {
                keys.add(flowKey.trim());
            }
        }
        return keys;
    }

    /**
     * 去掉 profileManaged 托管行；登录口误补的 Bearer 在确认前清掉。
     *
     * @return 是否删除了托管行
     */
    static boolean stripManagedAuthHeaders(
            Map<String, Object> data, String nodeLabel, List<String> warnings) {
        Object raw = data.get("headers");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean removed = false;
        String removedName = null;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) m;
            if (AuthHeaderResolver.isProfileManaged(row)) {
                removed = true;
                if (removedName == null) {
                    Object n = row.get("name");
                    if (n == null) {
                        n = row.get("key");
                    }
                    removedName = n != null ? String.valueOf(n).trim() : "Authorization";
                }
                continue;
            }
            rows.add(row);
        }
        if (!removed) {
            return false;
        }
        data.put("headers", rows);
        if (warnings != null) {
            warnings.add(AuthDesignWarningCodes.loginNoBearer(nodeLabel, removedName));
        }
        return true;
    }
}
