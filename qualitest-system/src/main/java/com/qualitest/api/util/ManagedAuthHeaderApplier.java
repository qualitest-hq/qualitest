package com.qualitest.api.util;

import java.util.List;
import java.util.Map;

/**
 * 将项目鉴权托管头写入 HTTP 节点 {@code data.headers}（造流 Normalizer / 批量刷新共用）。
 */
public final class ManagedAuthHeaderApplier {

    private ManagedAuthHeaderApplier() {}

    /**
     * 按接口鉴权与项目配置补齐或刷新托管头。
     *
     * @return 是否发生变更（新增或刷新了托管行）
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
        AuthHeaderResolver.ResolvedAuthHeader resolved = AuthHeaderResolver.resolve(
                apiAuthJson, projectAuthJson, apiPath);
        AuthHeaderResolver.ApplyResult applied = AuthHeaderResolver.applyToHeaderRows(data.get("headers"), resolved);
        data.put("headers", applied.headers());
        if (applied.changed() && warnings != null) {
            warnings.add(AuthDesignWarningCodes.headerManaged(
                    nodeLabel,
                    resolved.name() != null ? resolved.name() : "Authorization"));
        }
        return applied.changed();
    }
}
