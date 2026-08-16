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
 * 设计期检查：图中需要登录的 project HTTP，是否已有对应端的 flow 变量来源。
 * <p>
 * 对每个需鉴权的 project HTTP，解析其命中的鉴权 Profile，读取 loginHint.flowKey
 *（如 token、adminToken）。未配置 loginHint 的 Profile 跳过，不根据头模板猜测。
 * 再扫描整图是否已产出该 flowKey：HTTP extracts（scope=flow）、assign 赋值、
 * 子流 flowOutputs、场景 flowSeed。客户端与管理端分开检查，有一端 token 不能代替另一端。
 * <p>
 * 缺来源时返回错误文案（前缀 AUTH_TOKEN_MISSING），调用方应拒绝造流提交、Staging 确认或保存。
 * 项目未配置鉴权 Profile 时不做检查。
 */
public final class AuthTokenPresenceGate {

    private AuthTokenPresenceGate() {}

    /**
     * 检查合并后流程图的鉴权 token 来源是否齐全。
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

        // flowKey → Profile 展示名（同一 key 被多节点需要时只保留首次）
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
            return errors;
        }

        // 合并图（含 Staging pending）上的 extracts / assign / 子流输出 / flowSeed 均算来源
        Set<String> produced = collectProducedFlowKeys(graph);
        for (Map.Entry<String, String> entry : requiredKeys.entrySet()) {
            if (produced.contains(entry.getKey())) {
                continue;
            }
            errors.add(AuthDesignWarningCodes.tokenMissing(entry.getValue(), entry.getKey()));
        }
        return errors;
    }

    /**
     * 收集图中已声明会写入 flow 作用域的变量名。
     * 来源：HTTP extracts、assign 节点、subflow 输出、场景 flowSeed 的键。
     */
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

    /** 从 HTTP extracts 收集 scope=flow（或缺省 scope）的 name。 */
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

    /** 从 assign 列表收集 name 或 key。 */
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

    /** 从对象列表收集指定字段值。 */
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
