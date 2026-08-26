package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.model.ProjectAuthConfig;
import com.qualitest.api.model.ProjectAuthConfig.ProjectAuthProfile;
import com.qualitest.api.util.AuthDesignWarningCodes;
import com.qualitest.api.util.AuthHeaderResolver;
import com.qualitest.api.util.AuthHeaderResolver.ResolvedAuthHeader;
import com.qualitest.api.util.CredentialTargetSupport;
import com.qualitest.api.util.CredentialTargetSupport.CredentialTarget;
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
 * 设计期检查：图中需要登录的 project HTTP，是否已有对应端的凭证来源。
 * <p>
 * 对每个需鉴权的 project HTTP，解析其命中的鉴权 Profile，读取托管头上的
 * {@link CredentialTarget}（如 asset.adminAuth.token、flow.token）。
 * 未解析出凭证目标的 Profile 跳过。再扫描整图是否已产出该目标：
 * HTTP extracts（flow / asset）、assign 赋值、子流 flowOutputs、场景 flowSeed（仅 flow）。
 * 客户端与管理端分开检查，有一端凭证不能代替另一端。
 * <p>
 * 缺来源时返回错误文案（前缀 AUTH_TOKEN_MISSING），调用方应拒绝造流提交、Staging 确认或保存。
 * 项目未配置鉴权 Profile 时不做检查。
 */
public final class AuthTokenPresenceGate {

    private AuthTokenPresenceGate() {}

    /**
     * 检查合并后流程图的鉴权凭证来源是否齐全。
     *
     * @param graph           待检查的流程图
     * @param projectAuthJson 项目鉴权配置 JSON（authProfiles 等）
     * @param apiResolver     按接口 id 加载接口定义（含 path、auth 标签）
     * @return 错误列表；每条形如「AUTH_TOKEN_MISSING: …」；空列表表示无需检查或已满足
     */
    public static List<String> validate(
            GraphJson graph,
            String projectAuthJson,
            Function<Long, TestProjectApi> apiResolver) {
        List<String> errors = new ArrayList<>();
        if (graph == null || apiResolver == null) {
            return errors;
        }
        ProjectAuthConfig projectAuth = ProjectAuthConfigSupport.parse(projectAuthJson);
        if (ProjectAuthConfigSupport.isEmpty(projectAuth)) {
            return errors;
        }

        // identityKey → (displayPath, Profile 展示名)；同一目标被多节点需要时只保留首次
        Map<String, String> requiredDisplayPaths = new LinkedHashMap<>();
        Map<String, String> requiredProfileNames = new LinkedHashMap<>();
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
            List<CredentialTarget> targets = CredentialTargetSupport.targetsOnProfile(profile);
            if (targets.isEmpty()) {
                continue;
            }
            String profileName = ProjectAuthConfigSupport.displayProfileName(projectAuth, resolved.profileId());
            for (CredentialTarget target : targets) {
                if (requiredDisplayPaths.putIfAbsent(target.identityKey(), target.displayPath()) == null) {
                    requiredProfileNames.put(target.identityKey(), profileName);
                }
            }
        }

        if (requiredDisplayPaths.isEmpty()) {
            return errors;
        }

        Set<String> produced = collectProducedIdentityKeys(graph);
        for (Map.Entry<String, String> entry : requiredDisplayPaths.entrySet()) {
            if (produced.contains(entry.getKey())) {
                continue;
            }
            errors.add(AuthDesignWarningCodes.tokenMissing(
                    requiredProfileNames.get(entry.getKey()), entry.getValue()));
        }
        return errors;
    }

    /**
     * 收集图中已声明会写入的凭证 identityKey。
     * 来源：HTTP extracts（flow + asset）、assign、subflow 输出、场景 flowSeed（仅 flow）。
     */
    static Set<String> collectProducedIdentityKeys(GraphJson graph) {
        Set<String> keys = new LinkedHashSet<>();
        List<GraphNode> nodes = graph.getNodes() != null ? graph.getNodes() : List.of();
        for (GraphNode node : nodes) {
            if (node == null || node.getData() == null) {
                continue;
            }
            Map<String, Object> data = node.getData();
            for (CredentialTarget target : CredentialTargetSupport.listProducedTargets(data.get("extracts"))) {
                keys.add(target.identityKey());
            }
            String type = node.getType() != null ? node.getType().trim().toLowerCase() : "";
            if ("assign".equals(type)) {
                Object raw = data.get("assignments");
                if (raw == null) {
                    raw = data.get("items");
                }
                collectFlowNames(raw, keys);
            }
            if ("subflow".equals(type)) {
                Object raw = data.get("flowOutputs");
                if (raw == null) {
                    raw = data.get("outputs");
                }
                collectFlowField(raw, "flowKey", keys);
            }
        }
        GraphMeta meta = graph.getMeta();
        if (meta != null && meta.getScenarios() != null) {
            for (GraphRunScenario scenario : meta.getScenarios()) {
                if (scenario == null || scenario.getFlowSeed() == null) {
                    continue;
                }
                for (String seedKey : scenario.getFlowSeed().keySet()) {
                    CredentialTarget flowTarget = CredentialTarget.flow(seedKey);
                    if (flowTarget != null) {
                        keys.add(flowTarget.identityKey());
                    }
                }
            }
        }
        return keys;
    }

    /** 从 assign 列表收集 name/key，按 flow 目标写入 identityKey。 */
    private static void collectFlowNames(Object raw, Set<String> keys) {
        for (Object item : GraphDataLists.asList(raw)) {
            Map<?, ?> row = GraphDataLists.asMap(item);
            if (row == null) {
                continue;
            }
            Object name = row.get("name");
            if (name == null) {
                name = row.get("key");
            }
            addFlowIdentity(keys, name);
        }
    }

    /** 从对象列表收集指定字段值，按 flow 目标写入 identityKey。 */
    private static void collectFlowField(Object raw, String field, Set<String> keys) {
        for (Object item : GraphDataLists.asList(raw)) {
            Map<?, ?> row = GraphDataLists.asMap(item);
            if (row == null) {
                continue;
            }
            addFlowIdentity(keys, row.get(field));
        }
    }

    private static void addFlowIdentity(Set<String> keys, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        CredentialTarget target = CredentialTarget.flow(String.valueOf(value).trim());
        if (target != null) {
            keys.add(target.identityKey());
        }
    }
}
