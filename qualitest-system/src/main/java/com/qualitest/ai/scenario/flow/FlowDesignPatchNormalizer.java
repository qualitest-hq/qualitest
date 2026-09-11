package com.qualitest.ai.scenario.flow;

import cn.hutool.core.util.IdUtil;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.tools.FlowDesignIds;
import com.qualitest.api.util.ManagedAuthHeaderApplier;
import com.qualitest.flow.graph.ConditionBranchTerminalSupport;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.http.FlowHttpRequestBuilder;
import com.qualitest.flow.validate.AssertPathDesignGate;
import com.qualitest.flow.validate.AuthTokenPresenceGate;
import com.qualitest.flow.validate.HttpRequiredParamGate;
import com.qualitest.flow.validate.LoginExtractPresenceGate;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestFlowMapper;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * AI 产出流程补丁的服务端规范化器。
 * <p>
 * 在 AI submit 与 Web Diff 合并前依次执行：
 * <ol>
 *   <li>为 addNodes/addEdges 补雪花 id 与默认 position</li>
 *   <li>校验 HTTP(project) 节点 testProjectApiId 属于当前项目；external 跳过 API 归属校验</li>
 *   <li>按接口鉴权标签与项目鉴权配置补 Authorization 等托管头（profileManaged）</li>
 *   <li>补 data.summary（project / external / subflow 各自格式）</li>
 *   <li>规范化 scenarioPatch（场景 id、flowSeed 键名等）</li>
 *   <li>预合并到基准图副本，跑图结构校验，得到 errors/warnings</li>
 *   <li>用上游接口响应示例试算 assert/condition 的 http.body 左值；未命中记入 errors 回传模型</li>
 *   <li>检查需登录节点所需的 flow.token / flow.adminToken 等是否已有来源；缺则记入 errors，本次造流不可进入 Staging</li>
 *   <li>成功路径 HTTP 缺必填测值则记入 errors，本次造流不可进入 Staging</li>
 * </ol>
 * 不写库；用户在前端 Diff 确认后才持久化 graph_json。
 */
@Component
@RequiredArgsConstructor
public class FlowDesignPatchNormalizer {

    private static final double GRID_X = 380.0;
    private static final double DEFAULT_X = 40.0;
    private static final double DEFAULT_Y = 80.0;
    /** 与前端 flowConfig NODE_W / NODE_MIN_H 对齐，用于 AABB 避让 */
    private static final double NODE_W = 300.0;
    private static final double NODE_MIN_H = 108.0;
    /** 单次右移 / 下移行尝试上限；用尽后兜底落点，避免死循环 */
    private static final int MAX_SHIFT = 40;
    private static final double ROW_STEP = NODE_MIN_H + 40.0;

    private final TestProjectApiMapper testProjectApiMapper;
    private final TestProjectMapper testProjectMapper;
    private final TestFlowMapper testFlowMapper;
    private final GraphJsonValidator graphJsonValidator;
    private final FlowDesignPatchMerger patchMerger;

    /**
     * 全量规范化并预合并校验：合并 patch 后跑图结构校验与断言路径门禁，错误回传模型以便自我修正。
     */
    public NormalizeResult normalize(FlowDesignPatch patch, GraphJson baseGraph, Long testProjectId) {
        return normalize(patch, baseGraph, testProjectId, null);
    }

    /**
     * 全量规范化并预合并校验；{@code sessionClientIdMap} 为会话级短名→雪花映射（可 null），成功解析时就地写入。
     */
    public NormalizeResult normalize(FlowDesignPatch patch, GraphJson baseGraph, Long testProjectId,
                                     Map<String, String> sessionClientIdMap) {
        List<String> normWarnings = new ArrayList<>();
        List<String> idErrors = new ArrayList<>();
        Map<String, String> clientIdMap = sessionClientIdMap != null ? sessionClientIdMap : new HashMap<>();
        patch = initAndNormalizePatch(patch, baseGraph, testProjectId, normWarnings, clientIdMap, idErrors);

        if (!idErrors.isEmpty()) {
            DesignValidationResult failed = DesignValidationResult.builder()
                    .ok(false)
                    .errors(idErrors)
                    .warnings(normWarnings)
                    .build();
            return new NormalizeResult(patch, failed);
        }

        GraphJson merged = patchMerger.mergeAll(baseGraph, patch, normWarnings);
        GraphValidationResult validation = graphJsonValidator.validate(merged);
        List<String> warnings = new ArrayList<>(normWarnings);
        warnings.addAll(validation.getWarnings());

        List<String> errors = new ArrayList<>(validation.getErrors());
        // 断言路径：结构错误进 errors；schema 缺字段进 warnings（不阻断 Staging）
        AssertPathDesignGate.AssertPathGateResult assertPath =
                AssertPathDesignGate.validate(merged, apiResolver());
        errors.addAll(assertPath.errors());
        warnings.addAll(assertPath.warnings());
        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        Function<Long, TestProjectApi> apiResolver = apiResolver();
        errors.addAll(AuthTokenPresenceGate.validate(
                merged, projectAuthJson, apiResolver, subflowGraphResolver()));
        errors.addAll(LoginExtractPresenceGate.validate(merged, projectAuthJson, apiResolver));
        // 成功路径 HTTP 缺必填测值，本次造流不可进入 Staging
        errors.addAll(HttpRequiredParamGate.validate(merged, apiResolver));

        DesignValidationResult planValidation = DesignValidationResult.builder()
                .ok(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .build();

        return new NormalizeResult(patch, planValidation);
    }

    /**
     * 规范化 patch 字段（id、position、API 绑定、summary 等），不执行预合并校验。
     * 供部分勾选预览等在合并前单独调用规范化步骤的场景使用。
     */
    public FlowDesignPatch preparePatch(FlowDesignPatch patch, GraphJson baseGraph, Long testProjectId, List<String> warnings) {
        return initAndNormalizePatch(patch, baseGraph, testProjectId, warnings, new HashMap<>(), new ArrayList<>());
    }

    /**
     * 检查图中需登录的 project HTTP 是否已有对应端 flow 变量来源（如 token、adminToken）。
     * 缺来源时返回错误文案列表；项目未配鉴权或无需登录时返回空列表。
     */
    public List<String> collectAuthTokenPresenceErrors(GraphJson graph, Long testProjectId) {
        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        return AuthTokenPresenceGate.validate(
                graph, projectAuthJson, apiResolver(), subflowGraphResolver());
    }

    /** 按子流 id 加载 graph_json；mapper 未注入或解析失败时返回 null。 */
    private Function<Long, GraphJson> subflowGraphResolver() {
        if (testFlowMapper == null) {
            return id -> null;
        }
        return id -> {
            TestFlow sub = testFlowMapper.selectTestFlowById(id);
            if (sub == null || sub.getGraphJson() == null || sub.getGraphJson().isBlank()) {
                return null;
            }
            try {
                return GraphJson.parse(sub.getGraphJson());
            } catch (Exception e) {
                return null;
            }
        };
    }

    /**
     * 检查图中登录/注册类 project HTTP 是否已配置期望的 flow 变量 extract。
     * 缺 extract 时返回错误文案；非登录口或已配置时返回空列表。
     */
    public List<String> collectLoginExtractPresenceErrors(GraphJson graph, Long testProjectId) {
        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        return LoginExtractPresenceGate.validate(graph, projectAuthJson, apiResolver());
    }

    /**
     * 检查成功路径上的项目 HTTP 是否缺少必填测值。
     * 缺字段返回错误文案；节点关掉业务码校验或没有必填时返回空列表。
     */
    public List<String> collectHttpRequiredParamErrors(GraphJson graph) {
        return HttpRequiredParamGate.validate(graph, apiResolver());
    }

    /** 按接口 id 加载项目接口；mapper 未注入时一律返回 null。 */
    private Function<Long, TestProjectApi> apiResolver() {
        return testProjectApiMapper == null ? id -> null : testProjectApiMapper::selectTestProjectApiById;
    }

    /**
     * normalize 与 preparePatch 共用的 patch 初始化与规范化步骤。
     * 依次：suggestedDeletes、雪花 id / position、scenarioPatch、
     * HTTP API 绑定校验、按 type 规范化节点 data、节点 summary。
     */
    private FlowDesignPatch initAndNormalizePatch(
            FlowDesignPatch patch,
            GraphJson baseGraph,
            Long testProjectId,
            List<String> warnings,
            Map<String, String> clientIdMap,
            List<String> idErrors) {
        if (patch == null) {
            patch = new FlowDesignPatch();
        }
        if (patch.getSuggestedDeletes() == null) {
            patch.setSuggestedDeletes(new FlowDesignPatch.SuggestedDeletes());
        }
        normalizeIds(patch, baseGraph, clientIdMap, idErrors);
        if (idErrors != null && !idErrors.isEmpty()) {
            return patch;
        }
        reconcileConditionBranches(patch, baseGraph);
        pruneClientIdMapForDeletes(patch, clientIdMap);
        normalizeScenarioPatch(patch);
        validateApiBindings(patch, baseGraph, testProjectId, warnings);
        normalizeTypedNodeData(patch, baseGraph);
        fillSummaries(patch);
        return patch;
    }

    /**
     * 按节点 type 规范化 data（无 API 上下文）。
     * HTTP 已在 {@link #validateApiBindings} 中走 API-aware 规范化，此处跳过。
     * updateNodes 缺 type 时从基准图解析，避免 containsKey 启发式误伤。
     */
    private static void normalizeTypedNodeData(FlowDesignPatch patch, GraphJson baseGraph) {
        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                if (node == null || node.getData() == null) {
                    continue;
                }
                String type = trimType(node.getType());
                if (type.isEmpty() || "http".equalsIgnoreCase(type)) {
                    continue;
                }
                FlowDesignNodeDataNormalizer.normalize(type, node.getData());
            }
        }
        if (patch.getUpdateNodes() != null) {
            for (GraphNode update : patch.getUpdateNodes()) {
                if (update == null || update.getData() == null) {
                    continue;
                }
                String type = resolveEffectiveNodeType(baseGraph, update);
                if (type.isEmpty() || "http".equalsIgnoreCase(type)) {
                    continue;
                }
                FlowDesignNodeDataNormalizer.normalize(type, update.getData());
            }
        }
    }

    /** update 缺 type 时回落基准图节点 type。 */
    private static String resolveEffectiveNodeType(GraphJson baseGraph, GraphNode update) {
        if (update.getType() != null && !update.getType().isBlank()) {
            return update.getType().trim();
        }
        if (update.getId() == null || baseGraph == null || baseGraph.getNodes() == null) {
            return "";
        }
        GraphNode existing = GraphLookupUtils.findNode(baseGraph.getNodes(), update.getId());
        return existing != null && existing.getType() != null ? existing.getType().trim() : "";
    }

    private static String trimType(String type) {
        return type != null ? type.trim() : "";
    }

    /**
     * 规范化节点/边 id：AI 可用短名；画布侧一律雪花。
     * <p>
     * 会话 {@code clientIdMap} 保证同会话短名稳定映射；同步改写边端点、update 引用与 condition branches.target。
     */
    private void normalizeIds(FlowDesignPatch patch, GraphJson baseGraph,
                              Map<String, String> clientIdMap, List<String> idErrors) {
        if (clientIdMap == null) {
            clientIdMap = new HashMap<>();
        }
        if (idErrors == null) {
            idErrors = new ArrayList<>();
        }
        Set<String> knownIds = collectKnownNodeIds(baseGraph);
        Map<String, String> idRemap = new HashMap<>();
        List<GraphNodePosition> obstacles = collectBaseObstacles(baseGraph);

        if (patch.getAddNodes() != null) {
            int index = 0;
            for (GraphNode node : patch.getAddNodes()) {
                if (node == null) {
                    index++;
                    continue;
                }
                String oldId = node.getId() != null ? node.getId().trim() : "";
                String resolved = resolveAddNodeId(oldId, clientIdMap, idRemap);
                node.setId(resolved);
                knownIds.add(resolved);
                GraphNodePosition preferred = node.getPosition() != null
                        ? node.getPosition()
                        : defaultAddPosition(baseGraph, index);
                GraphNodePosition resolvedPos = resolveAddPosition(obstacles, preferred);
                node.setPosition(resolvedPos);
                obstacles.add(resolvedPos);
                index++;
            }
        }

        if (patch.getUpdateNodes() != null) {
            for (GraphNode update : patch.getUpdateNodes()) {
                if (update == null) {
                    continue;
                }
                String oldId = update.getId() != null ? update.getId().trim() : "";
                String resolved = resolveExistingRef(oldId, clientIdMap, knownIds, idRemap);
                if (resolved == null) {
                    idErrors.add("updateNodes 引用未知节点 id「" + oldId + "」：请使用本会话已登记短名、画布雪花 id，或先 addNodes");
                } else {
                    update.setId(resolved);
                }
            }
        }

        if (patch.getAddEdges() != null) {
            for (GraphEdge edge : patch.getAddEdges()) {
                if (edge == null) {
                    continue;
                }
                if (edge.getId() == null || edge.getId().isBlank() || !isValidId(edge.getId())) {
                    String oldEdgeId = edge.getId() != null ? edge.getId().trim() : "";
                    String newEdgeId = String.valueOf(IdUtil.getSnowflakeNextId());
                    if (!oldEdgeId.isEmpty() && !isValidId(oldEdgeId)) {
                        clientIdMap.put("edge:" + oldEdgeId, newEdgeId);
                        idRemap.put(oldEdgeId, newEdgeId);
                    }
                    edge.setId(newEdgeId);
                } else if (clientIdMap.containsKey("edge:" + edge.getId().trim())) {
                    edge.setId(clientIdMap.get("edge:" + edge.getId().trim()));
                }
                edge.setSource(remapEndpoint(edge.getSource(), clientIdMap, knownIds, idRemap, idErrors, "addEdges.source"));
                edge.setTarget(remapEndpoint(edge.getTarget(), clientIdMap, knownIds, idRemap, idErrors, "addEdges.target"));
            }
        }

        if (patch.getUpdateEdges() != null) {
            for (GraphEdge edge : patch.getUpdateEdges()) {
                if (edge == null) {
                    continue;
                }
                String edgeId = edge.getId() != null ? edge.getId().trim() : "";
                if (!edgeId.isEmpty()) {
                    String mapped = clientIdMap.get("edge:" + edgeId);
                    if (mapped != null) {
                        edge.setId(mapped);
                    } else if (!isValidId(edgeId)) {
                        idErrors.add("updateEdges 引用未知边 id「" + edgeId + "」");
                    }
                }
                if (edge.getSource() != null && !edge.getSource().isBlank()) {
                    edge.setSource(remapEndpoint(edge.getSource(), clientIdMap, knownIds, idRemap, idErrors, "updateEdges.source"));
                }
                if (edge.getTarget() != null && !edge.getTarget().isBlank()) {
                    edge.setTarget(remapEndpoint(edge.getTarget(), clientIdMap, knownIds, idRemap, idErrors, "updateEdges.target"));
                }
            }
        }

        remapConditionBranchTargets(patch.getAddNodes(), clientIdMap, idRemap);
        remapConditionBranchTargets(patch.getUpdateNodes(), clientIdMap, idRemap);

        if (patch.getSuggestedDeletes() != null) {
            remapDeleteIds(patch.getSuggestedDeletes().getNodeIds(), clientIdMap, idRemap);
            remapDeleteIds(patch.getSuggestedDeletes().getEdgeIds(), clientIdMap, idRemap);
        }
    }

    private static String resolveAddNodeId(String oldId, Map<String, String> clientIdMap,
                                           Map<String, String> idRemap) {
        if (oldId == null || oldId.isEmpty()) {
            return String.valueOf(IdUtil.getSnowflakeNextId());
        }
        String mapped = lookupMappedId(oldId, clientIdMap, idRemap);
        if (mapped != null) {
            idRemap.putIfAbsent(oldId, mapped);
            return mapped;
        }
        if (isValidId(oldId)) {
            return oldId;
        }
        String newId = String.valueOf(IdUtil.getSnowflakeNextId());
        clientIdMap.put(oldId, newId);
        idRemap.put(oldId, newId);
        return newId;
    }

    private static String resolveExistingRef(String oldId, Map<String, String> clientIdMap,
                                             Set<String> knownIds, Map<String, String> idRemap) {
        if (oldId == null || oldId.isEmpty()) {
            return null;
        }
        String mapped = lookupMappedId(oldId, clientIdMap, idRemap);
        if (mapped != null) {
            return mapped;
        }
        return isValidId(oldId) && knownIds.contains(oldId) ? oldId : null;
    }

    private static String remapEndpoint(String raw, Map<String, String> clientIdMap, Set<String> knownIds,
                                        Map<String, String> idRemap, List<String> idErrors, String where) {
        if (raw == null || raw.isBlank()) {
            idErrors.add(where + " 为空");
            return raw;
        }
        String oldId = raw.trim();
        String mapped = lookupMappedId(oldId, clientIdMap, idRemap);
        if (mapped != null) {
            return mapped;
        }
        if (knownIds.contains(oldId)) {
            return oldId;
        }
        idErrors.add(where + " 引用未知节点 id「" + oldId + "」");
        return oldId;
    }

    /** idRemap（本轮）优先，再查会话 clientIdMap。 */
    private static String lookupMappedId(String id, Map<String, String> clientIdMap, Map<String, String> idRemap) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        if (idRemap != null && idRemap.containsKey(id)) {
            return idRemap.get(id);
        }
        if (clientIdMap != null && clientIdMap.containsKey(id)) {
            return clientIdMap.get(id);
        }
        return null;
    }

    private static void remapConditionBranchTargets(List<GraphNode> nodes, Map<String, String> clientIdMap,
                                                    Map<String, String> idRemap) {
        if (nodes == null) {
            return;
        }
        for (GraphNode node : nodes) {
            if (node == null || node.getData() == null) {
                continue;
            }
            Object branchesObj = node.getData().get("branches");
            if (!(branchesObj instanceof List<?> branches)) {
                continue;
            }
            for (Object item : branches) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> branch = (Map<String, Object>) raw;
                Object targetObj = branch.get("target");
                if (targetObj == null) {
                    continue;
                }
                String target = String.valueOf(targetObj).trim();
                if (target.isEmpty()) {
                    continue;
                }
                String mapped = lookupMappedId(target, clientIdMap, idRemap);
                if (mapped != null) {
                    branch.put("target", mapped);
                }
            }
        }
    }

    private static void remapDeleteIds(List<String> ids, Map<String, String> clientIdMap,
                                       Map<String, String> idRemap) {
        if (ids == null) {
            return;
        }
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id == null || id.isBlank()) {
                continue;
            }
            String trimmed = id.trim();
            String mapped = lookupMappedId(trimmed, clientIdMap, idRemap);
            if (mapped != null) {
                ids.set(i, mapped);
            } else if (clientIdMap != null && clientIdMap.containsKey("edge:" + trimmed)) {
                ids.set(i, clientIdMap.get("edge:" + trimmed));
            }
        }
    }

    private static Set<String> collectKnownNodeIds(GraphJson baseGraph) {
        Set<String> ids = new HashSet<>();
        if (baseGraph == null || baseGraph.getNodes() == null) {
            return ids;
        }
        for (GraphNode n : baseGraph.getNodes()) {
            if (n != null && n.getId() != null && !n.getId().isBlank()) {
                ids.add(n.getId().trim());
            }
        }
        return ids;
    }

    private static void pruneClientIdMapForDeletes(FlowDesignPatch patch, Map<String, String> clientIdMap) {
        if (patch == null || patch.getSuggestedDeletes() == null || clientIdMap == null) {
            return;
        }
        List<String> nodeIds = patch.getSuggestedDeletes().getNodeIds();
        if (nodeIds != null && !nodeIds.isEmpty()) {
            FlowDesignClientIdMapSupport.pruneBySnowflakeIds(clientIdMap, nodeIds);
        }
        List<String> edgeIds = patch.getSuggestedDeletes().getEdgeIds();
        if (edgeIds != null && !edgeIds.isEmpty()) {
            Set<String> edgeDrop = new HashSet<>();
            for (String edgeId : edgeIds) {
                if (edgeId != null && !edgeId.isBlank()) {
                    edgeDrop.add(edgeId.trim());
                }
            }
            clientIdMap.entrySet().removeIf(e ->
                    e.getKey() != null && e.getKey().startsWith("edge:") && edgeDrop.contains(e.getValue()));
        }
    }

    /**
     * 将 condition 节点 branches 与出边对齐：出口只认边。
     * <p>
     * 每条分支按 label/handle 匹配出边写入 target；无匹配边则清除 target（结束分支）。
     * 不保留 AI 预写的 target。
     */
    @SuppressWarnings("unchecked")
    private static void reconcileConditionBranches(FlowDesignPatch patch, GraphJson baseGraph) {
        if (patch == null) {
            return;
        }
        Map<String, List<GraphEdge>> outEdgesBySource = new HashMap<>();
        collectOutEdges(baseGraph != null ? baseGraph.getEdges() : null, outEdgesBySource);
        collectOutEdges(patch.getAddEdges(), outEdgesBySource);

        List<GraphNode> nodes = new ArrayList<>();
        if (patch.getAddNodes() != null) {
            nodes.addAll(patch.getAddNodes());
        }
        if (patch.getUpdateNodes() != null) {
            nodes.addAll(patch.getUpdateNodes());
        }

        for (GraphNode node : nodes) {
            if (node == null || node.getData() == null) {
                continue;
            }
            String type = node.getType() != null ? node.getType().trim() : "";
            if (!type.isEmpty() && !"condition".equalsIgnoreCase(type)) {
                // update 可能缺 type，看 data.branches
                if (!(node.getData().get("branches") instanceof List<?>)) {
                    continue;
                }
            }
            Object branchesObj = node.getData().get("branches");
            if (!(branchesObj instanceof List<?> rawBranches) || rawBranches.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> branches = new ArrayList<>();
            for (Object item : rawBranches) {
                if (item instanceof Map<?, ?> m) {
                    branches.add((Map<String, Object>) m);
                }
            }
            if (branches.isEmpty()) {
                continue;
            }

            List<GraphEdge> outs = outEdgesBySource.getOrDefault(node.getId(), List.of());
            Set<String> usedEdgeIds = new HashSet<>();

            // 出口只认边：匹配出边则写 target，否则清除（结束）
            for (Map<String, Object> branch : branches) {
                String branchId = stringVal(branch.get("id"));
                String kind = branchKind(branch);
                GraphEdge matched = matchOutEdge(outs, branchId, kind, usedEdgeIds);
                if (matched != null) {
                    branch.put("target", matched.getTarget());
                    if (matched.getId() != null) {
                        usedEdgeIds.add(matched.getId());
                    }
                } else {
                    branch.remove("target");
                }
                ConditionBranchTerminalSupport.stripTerminalFlag(branch);
            }

            node.getData().put("branches", branches);
        }
    }

    private static void collectOutEdges(List<GraphEdge> edges, Map<String, List<GraphEdge>> outEdgesBySource) {
        if (edges == null) {
            return;
        }
        for (GraphEdge e : edges) {
            if (e == null || e.getSource() == null || e.getTarget() == null) {
                continue;
            }
            outEdgesBySource.computeIfAbsent(e.getSource().trim(), k -> new ArrayList<>()).add(e);
        }
    }

    private static GraphEdge matchOutEdge(List<GraphEdge> outs, String branchId, String kind, Set<String> usedEdgeIds) {
        if (outs == null || outs.isEmpty()) {
            return null;
        }
        String handle = branchId != null ? "out-" + branchId : null;
        boolean kindSet = kind != null && !kind.isEmpty();
        GraphEdge byKind = null;
        GraphEdge firstFree = null;
        for (GraphEdge e : outs) {
            if (e.getId() != null && usedEdgeIds.contains(e.getId())) {
                continue;
            }
            String label = e.getLabel() != null ? e.getLabel().trim() : "";
            if (handle != null && handle.equalsIgnoreCase(label)) {
                return e;
            }
            if (byKind == null && kindSet && kind.equalsIgnoreCase(label)) {
                byKind = e;
            }
            if (firstFree == null && label.isEmpty()) {
                // 仅无 label 的边可作为无 kind 分支的兜底，避免 if 抢走 label=else 的边
                firstFree = e;
            }
        }
        if (byKind != null) {
            return byKind;
        }
        // 有 kind 但未匹配到同名 label：不认边（结束或未接线）
        if (kindSet) {
            return null;
        }
        return firstFree;
    }

    /** 只读 {@code kind}，不做 type/name 回退。 */
    private static String branchKind(Map<String, Object> branch) {
        String kind = stringVal(branch.get("kind"));
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }

    private static String stringVal(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    /** 收集基准图全部节点 position，作为避让障碍起点。 */
    private static List<GraphNodePosition> collectBaseObstacles(GraphJson baseGraph) {
        List<GraphNodePosition> obstacles = new ArrayList<>();
        if (baseGraph == null || baseGraph.getNodes() == null) {
            return obstacles;
        }
        for (GraphNode n : baseGraph.getNodes()) {
            if (n != null && n.getPosition() != null) {
                obstacles.add(n.getPosition());
            }
        }
        return obstacles;
    }

    /**
     * 相对障碍物为候选点找空位：缺坐标已在调用方补 preferred；有坐标但重叠时同样错开。
     * 优先沿 x 网格右移，用尽后 y 下移再继续。
     */
    static GraphNodePosition resolveAddPosition(List<GraphNodePosition> obstacles, GraphNodePosition preferred) {
        double baseX = preferred != null ? preferred.getX() : DEFAULT_X;
        double baseY = preferred != null ? preferred.getY() : DEFAULT_Y;
        for (int row = 0; row < MAX_SHIFT; row++) {
            double y = baseY + row * ROW_STEP;
            for (int i = 0; i < MAX_SHIFT; i++) {
                double x = baseX + i * GRID_X;
                if (!overlapsAny(x, y, obstacles)) {
                    return GraphNodePosition.builder().x(x).y(y).build();
                }
            }
        }
        // 与前端一致：兜底落在扫过范围的右下角外侧
        return GraphNodePosition.builder()
                .x(baseX + MAX_SHIFT * GRID_X)
                .y(baseY + MAX_SHIFT * ROW_STEP)
                .build();
    }

    private static boolean overlapsAny(double x, double y, List<GraphNodePosition> obstacles) {
        if (obstacles == null || obstacles.isEmpty()) {
            return false;
        }
        for (GraphNodePosition o : obstacles) {
            if (o == null) {
                continue;
            }
            if (boxesOverlap(x, y, o.getX(), o.getY())) {
                return true;
            }
        }
        return false;
    }

    private static boolean boxesOverlap(double ax, double ay, double bx, double by) {
        return !(ax + NODE_W <= bx
                || bx + NODE_W <= ax
                || ay + NODE_MIN_H <= by
                || by + NODE_MIN_H <= ay);
    }

    /**
     * 规范化 scenarioPatch：为新增场景补雪花 id、空 flowSeed 与占位名称。
     */
    private void normalizeScenarioPatch(FlowDesignPatch patch) {
        FlowDesignScenarioPatch scenarioPatch = patch.getScenarioPatch();
        if (scenarioPatch == null || !FlowDesignScenarioPatch.hasChanges(scenarioPatch)) {
            return;
        }
        if (scenarioPatch.getAddScenarios() != null) {
            int index = 1;
            for (GraphRunScenario scenario : scenarioPatch.getAddScenarios()) {
                if (scenario == null) {
                    continue;
                }
                if (scenario.getId() == null || scenario.getId().isBlank() || !isValidId(scenario.getId())) {
                    scenario.setId(String.valueOf(IdUtil.getSnowflakeNextId()));
                }
                if (scenario.getFlowSeed() == null) {
                    scenario.setFlowSeed(new HashMap<>());
                }
                if (scenario.getName() == null || scenario.getName().isBlank()) {
                    scenario.setName("新场景 " + index);
                }
                if (scenario.getTestProjectEnvId() == null) {
                    scenario.setTestProjectEnvId("");
                }
                if (scenario.getRemark() == null) {
                    scenario.setRemark("");
                }
                index++;
            }
        }
    }

    /**
     * 为缺省 position 的新增节点推算坐标。
     * 空图首节点 (40,80)；否则取基准图末节点 x+380，同批后续节点 y 递增 40。
     */
    private static GraphNodePosition defaultAddPosition(GraphJson baseGraph, int indexInPatch) {
        double baseX = DEFAULT_X;
        double baseY = DEFAULT_Y;
        if (baseGraph != null && baseGraph.getNodes() != null && !baseGraph.getNodes().isEmpty()) {
            GraphNode last = baseGraph.getNodes().get(baseGraph.getNodes().size() - 1);
            if (last.getPosition() != null) {
                baseX = last.getPosition().getX() + GRID_X;
                baseY = last.getPosition().getY();
            }
        }
        return GraphNodePosition.builder()
                .x(baseX)
                .y(baseY + indexInPatch * 40.0)
                .build();
    }

    /** 画布节点/边 id 须为可解析的数字雪花 id */
    private static boolean isValidId(String id) {
        return FlowDesignIds.parseLongId(id) != null;
    }

    /** 校验 patch 中 HTTP 节点（新增或更新）绑定的 API 是否属于当前项目，并按项目鉴权补托管头 */
    private void validateApiBindings(FlowDesignPatch patch, GraphJson baseGraph, Long testProjectId, List<String> warnings) {
        String projectAuthJson = loadProjectAuthConfig(testProjectId);
        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                validateHttpNodeApiBinding(node, testProjectId, projectAuthJson, warnings);
            }
        }
        if (patch.getUpdateNodes() != null) {
            for (GraphNode update : patch.getUpdateNodes()) {
                GraphNode effective = resolveUpdateNodeForApiValidation(baseGraph, update);
                validateHttpNodeApiBinding(effective, testProjectId, projectAuthJson, warnings);
                syncApiBindingFieldsToUpdate(update, effective);
            }
        }
    }

    /**
     * 读取当前测试项目的鉴权配置 JSON。
     * 无项目 id、无 mapper 或项目不存在时返回 null。
     */
    private String loadProjectAuthConfig(Long testProjectId) {
        if (testProjectId == null || testProjectMapper == null) {
            return null;
        }
        TestProject project = testProjectMapper.selectTestProjectById(testProjectId);
        return project != null ? project.getAuthConfig() : null;
    }

    /**
     * updateNodes 的 patch 通常只含 data 增量；校验 API 绑定前与基准图节点合并 type/callMode 等上下文。
     */
    private static GraphNode resolveUpdateNodeForApiValidation(GraphJson baseGraph, GraphNode update) {
        if (update == null || update.getId() == null) {
            return update;
        }
        GraphNode existing = baseGraph != null && baseGraph.getNodes() != null
                ? GraphLookupUtils.findNode(baseGraph.getNodes(), update.getId())
                : null;
        if (existing == null) {
            return update;
        }
        String type = update.getType() != null && !update.getType().isBlank()
                ? update.getType()
                : existing.getType();
        Map<String, Object> mergedData = new HashMap<>();
        if (existing.getData() != null) {
            mergedData.putAll(existing.getData());
        }
        if (update.getData() != null) {
            mergedData.putAll(update.getData());
        }
        return GraphNode.builder()
                .id(update.getId())
                .type(type)
                .data(mergedData)
                .build();
    }

    /** 将 API 校验结果（置空/补全/鉴权头）写回 updateNodes 的增量 data */
    private static void syncApiBindingFieldsToUpdate(GraphNode update, GraphNode effective) {
        if (update == null || effective == null || effective.getData() == null) {
            return;
        }
        Map<String, Object> updateData = update.getData() != null
                ? new HashMap<>(update.getData())
                : new HashMap<>();
        Map<String, Object> effectiveData = effective.getData();
        if (updateData.containsKey("testProjectApiId") || effectiveData.containsKey("testProjectApiId")) {
            updateData.put("testProjectApiId", effectiveData.get("testProjectApiId"));
        }
        if (effectiveData.get("apiName") != null) {
            updateData.put("apiName", effectiveData.get("apiName"));
        }
        if (effectiveData.get("apiPath") != null) {
            updateData.put("apiPath", effectiveData.get("apiPath"));
        }
        if (effectiveData.containsKey("headers")) {
            updateData.put("headers", effectiveData.get("headers"));
        }
        update.setData(updateData);
    }

    /** 节点 data 含 testProjectApiId 时校验归属；external 模式校验 externalUrl 并跳过 API 绑定 */
    private void validateHttpNodeApiBinding(
            GraphNode node, Long testProjectId, String projectAuthJson, List<String> warnings) {
        if (node == null) {
            return;
        }
        Map<String, Object> data = node.getData();
        if (data == null) {
            return;
        }
        if (node.getType() != null && !node.getType().isBlank() && !"http".equals(node.getType())) {
            return;
        }
        String callMode = data.get("callMode") != null ? String.valueOf(data.get("callMode")).trim() : "";
        if (FlowHttpCallMode.isExternal(callMode)) {
            Object externalUrl = data.get("externalUrl");
            if (externalUrl == null || String.valueOf(externalUrl).isBlank()) {
                String nodeLabel = String.valueOf(data.getOrDefault("name", node.getId() != null ? node.getId() : "HTTP 节点"));
                warnings.add("HTTP 节点「" + nodeLabel + "」外联模式缺少 externalUrl");
            }
            FlowDesignHttpNodeNormalizer.normalize(data, null, projectAuthJson);
            return;
        }

        TestProjectApi boundApi = null;
        if (FlowHttpCallMode.isProject(callMode) || data.get("testProjectApiId") != null) {
            Object rawId = data.get("testProjectApiId");
            String nodeLabel = String.valueOf(data.getOrDefault("name", node.getId() != null ? node.getId() : "HTTP 节点"));
            if (rawId != null && !String.valueOf(rawId).isBlank()) {
                Long apiId = FlowDesignIds.parseLong(rawId);
                if (apiId == null) {
                    data.put("testProjectApiId", null);
                    warnings.add("HTTP 节点「" + nodeLabel + "」testProjectApiId 无效，已置空");
                } else {
                    TestProjectApi api = testProjectApiMapper != null
                            ? testProjectApiMapper.selectTestProjectApiById(apiId)
                            : null;
                    if (api == null || api.getTestProjectId() == null || !api.getTestProjectId().equals(testProjectId)) {
                        data.put("testProjectApiId", null);
                        warnings.add("HTTP 节点「" + nodeLabel + "」API 不属于当前项目，已置空");
                    } else {
                        data.put("testProjectApiId", String.valueOf(apiId));
                        if (data.get("apiName") == null) {
                            data.put("apiName", api.getApiName());
                        }
                        // 不自动写入 apiPath：路径只跟资产，节点不存路径
                        boundApi = api;
                        ManagedAuthHeaderApplier.applyToNodeData(
                                data,
                                api.getAuthConfig(),
                                projectAuthJson,
                                api.getApiPath(),
                                nodeLabel,
                                warnings);
                    }
                }
            }
        }

        FlowDesignHttpNodeNormalizer.normalize(data, boundApi, projectAuthJson);
    }

    /** 按节点类型生成画布卡片副标题 summary */
    private void fillSummaries(FlowDesignPatch patch) {
        if (patch.getAddNodes() == null) return;
        for (GraphNode node : patch.getAddNodes()) {
            Map<String, Object> data = node.getData();
            if (data == null) continue;
            switch (node.getType()) {
                case "http" -> data.put("summary", buildHttpSummary(data));
                case "assert" -> data.put("summary", buildAssertSummary(data));
                case "subflow" -> data.put("summary", buildSubflowSummary(data));
                default -> {
                    if (data.get("name") != null) {
                        data.put("summary", String.valueOf(data.get("name")));
                    }
                }
            }
        }
    }

    /**
     * 生成 HTTP 节点画布副标题 summary。
     * external：METHOD ↗ host/path；
     * project：METHOD +（节点 apiPath 若有，否则 apiName，再否则 —）。
     */
    private static String buildHttpSummary(Map<String, Object> data) {
        String callMode = data.get("callMode") != null ? String.valueOf(data.get("callMode")).trim() : "";
        if (FlowHttpCallMode.isExternal(callMode)) {
            String method = data.get("httpMethod") != null
                    ? String.valueOf(data.get("httpMethod")).toUpperCase(Locale.ROOT)
                    : "GET";
            String externalUrl = data.get("externalUrl") != null ? String.valueOf(data.get("externalUrl")) : "—";
            return FlowHttpRequestBuilder.formatExternalSummary(method, externalUrl);
        }
        if (data.get("testProjectApiId") == null) {
            return "请选择项目接口";
        }
        String method = data.get("httpMethod") != null
                ? String.valueOf(data.get("httpMethod")).toUpperCase(Locale.ROOT)
                : "GET";
        String path = null;
        if (data.get("apiPath") != null && !String.valueOf(data.get("apiPath")).isBlank()) {
            path = String.valueOf(data.get("apiPath"));
        } else if (data.get("apiName") != null && !String.valueOf(data.get("apiName")).isBlank()) {
            path = String.valueOf(data.get("apiName"));
        } else {
            path = "—";
        }
        return method + " " + path;
    }

    private static String buildAssertSummary(Map<String, Object> data) {
        Object rulesObj = data.get("rules");
        if (!(rulesObj instanceof List<?> rules) || rules.isEmpty()) {
            return "点击配置断言";
        }
        List<String> parts = new ArrayList<>();
        for (Object r : rules) {
            if (r instanceof Map<?, ?> rule) {
                Object left = rule.get("left");
                Object op = rule.get("operator");
                Object right = rule.get("right");
                if (left != null && String.valueOf(left).trim().length() > 0) {
                    parts.add(String.valueOf(left) + " " + String.valueOf(op) + " " + String.valueOf(right));
                }
            }
        }
        return parts.isEmpty() ? "点击配置断言" : String.join(" 且 ", parts);
    }

    /**
     * 生成子流节点画布副标题 {@code summary}。
     * <p>
     * 优先使用 {@code subflowName}，否则为「子流#subflowId」；若配置了 {@code outputs}，
     * 追加「→ flowKey1, flowKey2」表示写回父 flow 的变量键。
     */
    private static String buildSubflowSummary(Map<String, Object> data) {
        String name = data.get("subflowName") != null ? String.valueOf(data.get("subflowName")).trim() : "";
        if (name.isEmpty() && data.get("subflowId") != null && !String.valueOf(data.get("subflowId")).isBlank()) {
            name = "子流#" + data.get("subflowId");
        }
        if (name.isEmpty()) {
            return "请选择子流";
        }
        Object outputs = data.get("outputs");
        if (outputs instanceof List<?> rows && !rows.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map) {
                    Object flowKey = map.get("flowKey");
                    if (flowKey != null && !String.valueOf(flowKey).isBlank()) {
                        keys.add(String.valueOf(flowKey).trim());
                    }
                }
            }
            if (!keys.isEmpty()) {
                return name + " → " + String.join(", ", keys);
            }
        }
        return name;
    }

    /** 规范化后的 patch 与校验结果 */
    public record NormalizeResult(FlowDesignPatch patch, DesignValidationResult validation) {}
}
