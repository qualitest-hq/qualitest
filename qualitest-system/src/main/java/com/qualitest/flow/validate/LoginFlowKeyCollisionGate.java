package com.qualitest.flow.validate;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.util.AuthDesignWarningCodes;
import com.qualitest.api.util.CredentialTargetSupport;
import com.qualitest.api.util.CredentialTargetSupport.CredentialTarget;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.project.domain.TestProjectApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 设计期门禁：两套不同登录口不得写出同一个凭证目标。
 * <p>
 * 扫描 extracts 已写出托管头凭证目标的 project HTTP；按实际 extracts 收集目标。
 * 同一 identityKey 被至少两个不同 method+path 写出时硬拦。
 */
public final class LoginFlowKeyCollisionGate {

    private LoginFlowKeyCollisionGate() {}

    /**
     * @param graph           待检查画布
     * @param projectAuthJson 项目鉴权配置
     * @param apiResolver     按 testProjectApiId 取接口
     * @return 错误文案列表；空表示通过
     */
    public static List<String> validate(
            GraphJson graph,
            String projectAuthJson,
            Function<Long, TestProjectApi> apiResolver) {
        List<String> errors = new ArrayList<>();
        // identityKey → 写出该目标的登录口 method+path
        Map<String, Set<String>> keyToEndpoints = new LinkedHashMap<>();
        Map<String, String> keyToDisplayPath = new LinkedHashMap<>();
        CredentialLoginHttpVisitor.visit(graph, projectAuthJson, apiResolver, (node, ignored, api) -> {
            String endpoint = resolveEndpoint(node, api);
            Object extracts = node.getData().get("extracts");
            for (CredentialTarget target : CredentialTargetSupport.listProducedTargets(extracts)) {
                keyToEndpoints.computeIfAbsent(target.identityKey(), k -> new LinkedHashSet<>()).add(endpoint);
                keyToDisplayPath.putIfAbsent(target.identityKey(), target.displayPath());
            }
        });
        for (Map.Entry<String, Set<String>> entry : keyToEndpoints.entrySet()) {
            if (entry.getValue().size() >= 2) {
                errors.add(AuthDesignWarningCodes.loginFlowKeyCollision(
                        keyToDisplayPath.get(entry.getKey())));
            }
        }
        return errors;
    }

    /** method + 规范化 path，用于区分两套登录口。 */
    static String resolveEndpoint(GraphNode node, TestProjectApi api) {
        String method = resolveHttpMethod(node, api);
        String path = ProjectAuthConfigSupport.normalizeApiPath(api != null ? api.getApiPath() : null);
        return method + " " + path;
    }

    private static String resolveHttpMethod(GraphNode node, TestProjectApi api) {
        if (node != null && node.getData() != null) {
            Object m = node.getData().get("httpMethod");
            if (m != null && StrUtil.isNotBlank(String.valueOf(m))) {
                return String.valueOf(m).trim().toUpperCase(Locale.ROOT);
            }
        }
        if (api != null && StrUtil.isNotBlank(api.getRequestConfig())) {
            try {
                JSONObject rc = JSON.parseObject(api.getRequestConfig());
                if (rc != null && StrUtil.isNotBlank(rc.getString("method"))) {
                    return rc.getString("method").trim().toUpperCase(Locale.ROOT);
                }
            } catch (Exception ignored) {
                // 解析失败则回落 POST
            }
        }
        return "POST";
    }
}
