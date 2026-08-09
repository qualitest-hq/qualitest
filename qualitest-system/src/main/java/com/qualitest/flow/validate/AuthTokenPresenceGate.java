package com.qualitest.flow.validate;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.AuthDesignWarningCodes;
import com.qualitest.api.util.AuthHeaderResolver;
import com.qualitest.api.util.AuthHeaderResolver.ResolvedAuthHeader;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.flow.graph.FlowHttpNodeVisitor;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.project.domain.TestProjectApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 设计期鉴权 token 来源软提示（不硬拦）。
 * <p>
 * 扫描图中需登录的 project HTTP：按命中 Profile 的 {@code loginHint.flowKey}
 * 检查图中是否有对应来源。未配置 loginHint 则跳过该节点（不猜头模板）。
 * 客户端 / 管理端分端检查，禁止「有任一 token 即过」。
 */
public final class AuthTokenPresenceGate {

    private AuthTokenPresenceGate() {}

    /**
     * @param graph           合并后的图
     * @param projectAuthJson 项目鉴权配置 JSON
     * @param apiResolver     按 id 加载接口
     * @return soft warnings；空列表表示无需提示或已满足
     */
    public static List<String> warn(
            GraphJson graph,
            String projectAuthJson,
            Function<Long, TestProjectApi> apiResolver) {
        List<String> warnings = new ArrayList<>();
        if (graph == null || apiResolver == null) {
            return warnings;
        }
        ProjectAuthConfig projectAuth = ProjectAuthConfigSupport.parse(projectAuthJson);
        if (ProjectAuthConfigSupport.isEmpty(projectAuth)) {
            return warnings;
        }

        // flowKey → 展示用 profile 名（多节点复用同一 key 时保留首次）
        Map<String, String> requiredKeys = new LinkedHashMap<>();
        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();
        for (GraphNode node : nodes) {
            if (node == null || node.getData() == null) {
                continue;
            }
            String type = node.getType() != null ? node.getType().trim().toLowerCase() : "";
            if (!type.isEmpty() && !"http".equals(type)) {
                continue;
            }
            JSONObject data = new JSONObject(node.getData());
            if (!FlowHttpNodeVisitor.isProjectBoundHttp(data)) {
                continue;
            }
            Long apiId = FlowHttpNodeVisitor.parseTestProjectApiId(data.get("testProjectApiId"));
            if (apiId == null) {
                continue;
            }
            TestProjectApi api = apiResolver.apply(apiId);
            if (api == null) {
                continue;
            }
            ResolvedAuthHeader resolved = AuthHeaderResolver.resolve(
                    api.getAuthConfig(), projectAuthJson, api.getApiPath());
            if (resolved == null || resolved.skipped()) {
                continue;
            }
            ProjectAuthProfile profile = ProjectAuthConfigSupport.findProfile(projectAuth, resolved.profileId());
            String flowKey = ProjectAuthConfigSupport.resolveLoginFlowKey(profile);
            if (StrUtil.isBlank(flowKey)) {
                continue;
            }
            requiredKeys.putIfAbsent(
                    flowKey, ProjectAuthConfigSupport.displayProfileName(projectAuth, resolved.profileId()));
        }

        if (requiredKeys.isEmpty()) {
            return warnings;
        }

        Set<String> produced = collectProducedFlowKeys(graph);
        for (Map.Entry<String, String> entry : requiredKeys.entrySet()) {
            if (produced.contains(entry.getKey())) {
                continue;
            }
            warnings.add(AuthDesignWarningCodes.tokenMissing(entry.getValue(), entry.getKey()));
        }
        return warnings;
    }

    static Set<String> collectProducedFlowKeys(GraphJson graph) {
        Set<String> keys = new LinkedHashSet<>();
        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();
        for (GraphNode node : nodes) {
            if (node == null || node.getData() == null) {
                continue;
            }
            Map<String, Object> data = node.getData();
            collectFromExtracts(data.get("extracts"), keys);
            String type = node.getType() != null ? node.getType().trim().toLowerCase() : "";
            if ("assign".equals(type)) {
                Object raw = data.get("assignments");
                if (raw == null) {
                    raw = data.get("items");
                }
                collectNameOrKey(raw, keys);
            }
            if ("subflow".equals(type)) {
                Object raw = data.get("flowOutputs");
                if (raw == null) {
                    raw = data.get("outputs");
                }
                collectField(raw, "flowKey", keys);
            }
        }
        GraphMeta meta = graph.getMeta();
        if (meta != null && meta.getScenarios() != null) {
            for (GraphRunScenario scenario : meta.getScenarios()) {
                if (scenario == null || scenario.getFlowSeed() == null) {
                    continue;
                }
                for (String seedKey : scenario.getFlowSeed().keySet()) {
                    if (StrUtil.isNotBlank(seedKey)) {
                        keys.add(seedKey.trim());
                    }
                }
            }
        }
        return keys;
    }

    private static void collectFromExtracts(Object raw, Set<String> keys) {
        for (Object item : GraphDataLists.asList(raw)) {
            Map<?, ?> row = GraphDataLists.asMap(item);
            if (row == null) {
                continue;
            }
            Object scope = row.get("scope");
            if (scope != null && !String.valueOf(scope).isBlank()
                    && !"flow".equalsIgnoreCase(String.valueOf(scope).trim())) {
                continue;
            }
            addTrimmed(keys, row.get("name"));
        }
    }

    private static void collectNameOrKey(Object raw, Set<String> keys) {
        for (Object item : GraphDataLists.asList(raw)) {
            Map<?, ?> row = GraphDataLists.asMap(item);
            if (row == null) {
                continue;
            }
            Object name = row.get("name");
            if (name == null) {
                name = row.get("key");
            }
            addTrimmed(keys, name);
        }
    }

    private static void collectField(Object raw, String field, Set<String> keys) {
        for (Object item : GraphDataLists.asList(raw)) {
            Map<?, ?> row = GraphDataLists.asMap(item);
            if (row == null) {
                continue;
            }
            addTrimmed(keys, row.get(field));
        }
    }

    private static void addTrimmed(Set<String> keys, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            keys.add(String.valueOf(value).trim());
        }
    }
}
