package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmRequest;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignSavePrecheckResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.validate.AssertPathDesignGate;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationOptions;
import com.qualitest.flow.validate.GraphValidationResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 单条 Staging 单元确认：用户点 ✓ 后在服务端合并该单元并校验，不写库。
 * <p>
 * 流程：合并 draft → 规范化 patch → 依赖检查 → 按 unitId 合入图副本 →
 * 图结构校验 → 对本单元相关节点做断言路径校验。<br>
 * 若本轮已无未决单元，响应可附带运行风险预警（鉴权/登录抽取/HTTP 必填），
 * 不阻断本次确认，也不阻断之后的保存。<br>
 * 成功返回 graphJson，由前端写回编辑态并清 Staging 标记。
 */
@Service
@RequiredArgsConstructor
public class FlowDesignPatchConfirmService {

    private static final Logger log = LoggerFactory.getLogger(FlowDesignPatchConfirmService.class);

    private final FlowDesignPatchNormalizer patchNormalizer;
    private final FlowDesignPatchMerger patchMerger;
    private final GraphJsonValidator graphJsonValidator;

    /**
     * 确认单个 Staging 单元。
     *
     * @param request 当前图、patch、unitId、可选 draft、已确认/已拒绝单元 id
     * @return ok、errors/warnings、成功时的 graphJson、可选 saveRiskWarnings 与 baseGraphHash
     */
    public FlowDesignPatchConfirmResult confirmUnit(FlowDesignPatchConfirmRequest request) {
        long startedAt = System.nanoTime();
        List<String> warnings = new ArrayList<>();

        if (request == null || request.getPatch() == null) {
            return failureResult(List.of("patch 不能为空"), warnings, List.of(), List.of(), "");
        }

        String unitId = normalizeUnitId(request.getUnitId());
        if (unitId == null) {
            return failureResult(List.of("unitId 不能为空"), warnings, List.of(), List.of(), "");
        }

        GraphJson baseGraph = request.getGraphJson() != null
                ? request.getGraphJson()
                : GraphJson.builder().build();
        String baseGraphHash = GraphJsonHashUtil.computeBaseGraphHash(baseGraph);

        FlowDesignPatch patch = JSON.parseObject(JSON.toJSONString(request.getPatch()), FlowDesignPatch.class);
        applyDraftOverride(patch, unitId, request.getDraftOverride());

        List<String> prepareErrors = new ArrayList<>();
        patchNormalizer.preparePatch(patch, baseGraph, request.getTestProjectId(), warnings, prepareErrors);
        if (!prepareErrors.isEmpty()) {
            logConfirmMetrics(startedAt, unitId, false);
            return failureResult(prepareErrors, warnings, List.of(), List.of(), baseGraphHash);
        }

        GraphJson workingGraph = FlowDesignPatchMerger.cloneGraph(baseGraph);
        applyUnitDraftToGraph(workingGraph, patch, unitId, request.getDraftOverride());

        Set<String> confirmedUnitIds = normalizeConfirmedUnitIds(request.getConfirmedUnitIds());
        Set<String> rejectedUnitIds = normalizeConfirmedUnitIds(request.getRejectedUnitIds());
        List<String> dependencyHints = computeDependencyHints(
                patch, unitId, confirmedUnitIds, rejectedUnitIds, baseGraph);
        if (!dependencyHints.isEmpty()) {
            List<String> errors = new ArrayList<>(dependencyHints);
            logConfirmMetrics(startedAt, unitId, false);
            return failureResult(errors, warnings, dependencyHints, List.of(), baseGraphHash);
        }

        // 场景未绑环境：只记 warning，不阻断确认
        noteScenarioEnvWarning(patch, unitId, warnings);

        Set<String> acceptedIds = Set.of(unitId);
        GraphJson merged = patchMerger.merge(workingGraph, patch, acceptedIds, warnings);
        boolean deferTopology = FlowDesignPatchUnitIds.hasUnresolvedUnits(
                patch, confirmedUnitIds, rejectedUnitIds, unitId);
        GraphValidationOptions validationOptions = deferTopology
                ? GraphValidationOptions.stagingPartialConfirm()
                : GraphValidationOptions.full();
        GraphValidationResult validation = graphJsonValidator.validate(merged, validationOptions);
        List<String> allWarnings = new ArrayList<>(warnings);
        allWarnings.addAll(validation.getWarnings());
        List<String> allErrors = new ArrayList<>(validation.getErrors());

        // 对本单元节点跑断言路径校验；确认的是边/场景等时，他人断言错误只进 warnings
        AssertPathDesignGate.AssertPathGateResult assertPath =
                AssertPathDesignGate.validate(merged, patchNormalizer.apiResolver());
        Set<String> touchIds = unitNodeIdsForConfirm(unitId);
        for (String err : assertPath.errors()) {
            if (!touchIds.isEmpty() && FlowDesignPatchNormalizer.touchesUnitNode(err, touchIds)) {
                allErrors.add(err);
            } else {
                allWarnings.add(err);
            }
        }
        allWarnings.addAll(assertPath.warnings());

        List<String> saveRiskWarnings = List.of();
        if (allErrors.isEmpty() && !deferTopology) {
            // 本轮已无未决单元：跑保存预检，结果放入 saveRiskWarnings（不硬拦本次确认）
            FlowDesignSavePrecheckResult precheck =
                    patchNormalizer.savePrecheck(merged, request.getTestProjectId());
            if (precheck.getErrors() != null && !precheck.getErrors().isEmpty()) {
                saveRiskWarnings = List.copyOf(precheck.getErrors());
                allWarnings.addAll(saveRiskWarnings);
            }
        }

        boolean ok = allErrors.isEmpty();
        logConfirmMetrics(startedAt, unitId, ok);

        if (!ok) {
            return FlowDesignPatchConfirmResult.builder()
                    .ok(false)
                    .errors(allErrors)
                    .warnings(allWarnings)
                    .graphJson(null)
                    .dependencyHints(List.of())
                    .saveRiskWarnings(List.of())
                    .baseGraphHash(baseGraphHash)
                    .build();
        }

        return FlowDesignPatchConfirmResult.builder()
                .ok(true)
                .errors(List.of())
                .warnings(allWarnings)
                .graphJson(merged)
                .dependencyHints(List.of())
                .saveRiskWarnings(saveRiskWarnings)
                .baseGraphHash(baseGraphHash)
                .build();
    }

    private static FlowDesignPatchConfirmResult failureResult(
            List<String> errors,
            List<String> warnings,
            List<String> dependencyHints,
            List<String> saveRiskWarnings,
            String baseGraphHash) {
        return FlowDesignPatchConfirmResult.builder()
                .ok(false)
                .errors(errors)
                .warnings(warnings)
                .graphJson(null)
                .dependencyHints(dependencyHints)
                .saveRiskWarnings(saveRiskWarnings)
                .baseGraphHash(baseGraphHash)
                .build();
    }

    /** 从 unitId 取出本单元涉及的节点 id；仅 addNode/updateNode 有值，其它 kind 返回空集 */
    private static Set<String> unitNodeIdsForConfirm(String unitId) {
        Set<String> ids = new HashSet<>();
        if (unitId.startsWith("addNode:") || unitId.startsWith("updateNode:")) {
            ids.add(unitId.substring(unitId.indexOf(':') + 1).trim());
        }
        return ids;
    }

    private void logConfirmMetrics(long startedAt, String unitId, boolean ok) {
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.debug("ai_patch_confirm_ms={} unit_id={} ok={}", elapsedMs, unitId, ok);
    }

    /** 空白 unitId 视为无效，返回 null */
    private static String normalizeUnitId(String unitId) {
        if (unitId == null || unitId.isBlank()) {
            return null;
        }
        return unitId.trim();
    }

    /** 去掉空白项，得到已确认或已拒绝的 unitId 集合 */
    private static Set<String> normalizeConfirmedUnitIds(List<String> confirmedUnitIds) {
        Set<String> normalized = new HashSet<>();
        if (confirmedUnitIds == null) {
            return normalized;
        }
        for (String id : confirmedUnitIds) {
            if (id != null && !id.isBlank()) {
                normalized.add(id.trim());
            }
        }
        return normalized;
    }

    /**
     * 把前端 draft 覆盖进 patch 里 unitId 对应的那一项（节点 / 边 / 场景）。
     */
    private void applyDraftOverride(FlowDesignPatch patch, String unitId, Object draftOverride) {
        if (draftOverride == null) {
            return;
        }
        JSONObject draft = draftOverride instanceof JSONObject
                ? (JSONObject) draftOverride
                : JSON.parseObject(JSON.toJSONString(draftOverride));

        if (unitId.startsWith("addNode:") || unitId.startsWith("updateNode:")) {
            String nodeId = unitId.substring(unitId.indexOf(':') + 1);
            List<GraphNode> nodes = unitId.startsWith("addNode:")
                    ? patch.getAddNodes()
                    : patch.getUpdateNodes();
            for (GraphNode node : nodes) {
                if (node != null && nodeId.equals(node.getId())) {
                    GraphNode override = draft.to(GraphNode.class);
                    FlowDesignPatchMerger.applyNodeUpdate(node, override);
                    return;
                }
            }
            return;
        }

        if (unitId.startsWith("addEdge:") || unitId.startsWith("updateEdge:")) {
            String edgeId = unitId.substring(unitId.indexOf(':') + 1);
            List<GraphEdge> edges = unitId.startsWith("addEdge:")
                    ? patch.getAddEdges()
                    : patch.getUpdateEdges();
            for (GraphEdge edge : edges) {
                if (edge != null && edgeId.equals(edge.getId())) {
                    GraphEdge override = draft.to(GraphEdge.class);
                    FlowDesignPatchMerger.applyEdgeUpdate(edge, override);
                    return;
                }
            }
            return;
        }

        if (unitId.startsWith("addScenario:") || unitId.startsWith("updateScenario:")) {
            String scenarioId = unitId.substring(unitId.indexOf(':') + 1);
            FlowDesignScenarioPatch scenarioPatch = patch.getScenarioPatch();
            if (scenarioPatch == null) {
                return;
            }
            List<GraphRunScenario> scenarios = unitId.startsWith("addScenario:")
                    ? scenarioPatch.getAddScenarios()
                    : scenarioPatch.getUpdateScenarios();
            for (GraphRunScenario scenario : scenarios) {
                if (scenario != null && scenarioId.equals(scenario.getId())) {
                    GraphRunScenario override = draft.to(GraphRunScenario.class);
                    // 写入 remark、失败策略、flowSeed 等场景字段
                    ScenarioPatchMergeSupport.applyScenarioUpdate(scenario, override);
                    return;
                }
            }
            return;
        }
    }

    /**
     * 将 patch / draft 写入画布上已注入的 Staging 对象（merge 对 add 类「已存在则跳过」时需此步）。
     */
    private void applyUnitDraftToGraph(
            GraphJson graph,
            FlowDesignPatch patch,
            String unitId,
            Object draftOverride) {
        if (unitId.startsWith("addNode:") || unitId.startsWith("updateNode:")) {
            String nodeId = unitId.substring(unitId.indexOf(':') + 1);
            GraphNode graphNode = GraphLookupUtils.findNode(graph.getNodes(), nodeId);
            if (graphNode == null) {
                return;
            }
            List<GraphNode> patchNodes = unitId.startsWith("addNode:")
                    ? patch.getAddNodes()
                    : patch.getUpdateNodes();
            for (GraphNode patchNode : patchNodes) {
                if (patchNode != null && nodeId.equals(patchNode.getId())) {
                    FlowDesignPatchMerger.applyNodeUpdate(graphNode, patchNode);
                    break;
                }
            }
            applyGraphNodeDraft(graphNode, draftOverride);
            // draft 可能冲掉规范化补全的字段；按节点 type 再整理一次 data
            if (graphNode.getData() != null) {
                FlowDesignNodeDataNormalizer.normalize(graphNode.getType(), graphNode.getData());
            }
            return;
        }

        if (unitId.startsWith("addEdge:") || unitId.startsWith("updateEdge:")) {
            String edgeId = unitId.substring(unitId.indexOf(':') + 1);
            GraphEdge graphEdge = GraphLookupUtils.findEdge(graph.getEdges(), edgeId);
            if (graphEdge == null) {
                return;
            }
            List<GraphEdge> patchEdges = unitId.startsWith("addEdge:")
                    ? patch.getAddEdges()
                    : patch.getUpdateEdges();
            for (GraphEdge patchEdge : patchEdges) {
                if (patchEdge != null && edgeId.equals(patchEdge.getId())) {
                    FlowDesignPatchMerger.applyEdgeUpdate(graphEdge, patchEdge);
                    break;
                }
            }
            applyGraphEdgeDraft(graphEdge, draftOverride);
        }
    }

    private static void applyGraphNodeDraft(GraphNode graphNode, Object draftOverride) {
        if (draftOverride == null) {
            return;
        }
        JSONObject draft = draftOverride instanceof JSONObject
                ? (JSONObject) draftOverride
                : JSON.parseObject(JSON.toJSONString(draftOverride));
        FlowDesignPatchMerger.applyNodeUpdate(graphNode, draft.to(GraphNode.class));
    }

    private static void applyGraphEdgeDraft(GraphEdge graphEdge, Object draftOverride) {
        if (draftOverride == null) {
            return;
        }
        JSONObject draft = draftOverride instanceof JSONObject
                ? (JSONObject) draftOverride
                : JSON.parseObject(JSON.toJSONString(draftOverride));
        FlowDesignPatchMerger.applyEdgeUpdate(graphEdge, draft.to(GraphEdge.class));
    }

    /**
     * 确认依赖检查（不满足则阻断）：
     * addEdge/updateEdge 的端点若是本 patch 未确认的 addNode，须先确认该节点；
     * 端点不在底图也不在本 patch addNodes 时记错；
     * deleteNode 时，本 patch 里仍 pending 且指向该节点的 addEdge 须先确认或拒绝。
     */
    private List<String> computeDependencyHints(
            FlowDesignPatch patch,
            String unitId,
            Set<String> confirmedUnitIds,
            Set<String> rejectedUnitIds,
            GraphJson baseGraph) {
        List<String> hints = new ArrayList<>();
        Set<String> resolved = new HashSet<>(confirmedUnitIds);
        resolved.addAll(rejectedUnitIds);

        Set<String> addNodeIdsInPatch = new HashSet<>();
        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                if (node != null && node.getId() != null && !node.getId().isBlank()) {
                    addNodeIdsInPatch.add(node.getId().trim());
                }
            }
        }

        if (unitId.startsWith("addEdge:") || unitId.startsWith("updateEdge:")) {
            GraphEdge edge = findPatchEdge(patch, unitId);
            if (edge != null) {
                String edgeId = edge.getId() != null ? edge.getId().trim() : "";
                checkAddNodeDependency(hints, unitId, edgeId, edge.getSource(), addNodeIdsInPatch, confirmedUnitIds);
                checkAddNodeDependency(hints, unitId, edgeId, edge.getTarget(), addNodeIdsInPatch, confirmedUnitIds);
                // 端点既不在基准图也不在本 patch addNodes
                checkEndpointExists(hints, unitId, edge.getSource(), addNodeIdsInPatch, baseGraph, "source");
                checkEndpointExists(hints, unitId, edge.getTarget(), addNodeIdsInPatch, baseGraph, "target");
            }
            return hints;
        }

        if (unitId.startsWith("deleteNode:")) {
            String nodeId = unitId.substring("deleteNode:".length()).trim();
            if (patch.getAddEdges() != null) {
                for (GraphEdge edge : patch.getAddEdges()) {
                    if (edge == null || edge.getId() == null) {
                        continue;
                    }
                    String edgeKey = "addEdge:" + edge.getId().trim();
                    if (resolved.contains(edgeKey)) {
                        continue;
                    }
                    String src = edge.getSource() != null ? edge.getSource().trim() : "";
                    String tgt = edge.getTarget() != null ? edge.getTarget().trim() : "";
                    if (nodeId.equals(src) || nodeId.equals(tgt)) {
                        hints.add("确认 " + unitId + " 前请先确认或拒绝仍指向该节点的 " + edgeKey);
                    }
                }
            }
        }
        return hints;
    }

    /** 场景增改单元未填 testProjectEnvId 时写入 warning，不阻断确认 */
    private static void noteScenarioEnvWarning(FlowDesignPatch patch, String unitId, List<String> warnings) {
        if (!unitId.startsWith("addScenario:") && !unitId.startsWith("updateScenario:")) {
            return;
        }
        GraphRunScenario scenario = findPatchScenario(patch, unitId);
        if (scenario != null
                && (scenario.getTestProjectEnvId() == null || scenario.getTestProjectEnvId().isBlank())) {
            warnings.add("场景 " + unitId + " 未绑定 testProjectEnvId，保存后运行前请选择环境");
        }
    }

    /** 按 unitId 从 patch 的 addEdges 或 updateEdges 取出对应边 */
    private static GraphEdge findPatchEdge(FlowDesignPatch patch, String unitId) {
        boolean add = unitId.startsWith("addEdge:");
        String edgeId = unitId.substring(unitId.indexOf(':') + 1).trim();
        List<GraphEdge> edges = add ? patch.getAddEdges() : patch.getUpdateEdges();
        if (edges == null) {
            return null;
        }
        for (GraphEdge candidate : edges) {
            if (candidate != null && edgeId.equals(candidate.getId())) {
                return candidate;
            }
        }
        return null;
    }

    /** 按 unitId 从 patch 的 addScenarios 或 updateScenarios 取出对应场景 */
    private static GraphRunScenario findPatchScenario(FlowDesignPatch patch, String unitId) {
        FlowDesignScenarioPatch sp = patch.getScenarioPatch();
        if (sp == null) {
            return null;
        }
        boolean add = unitId.startsWith("addScenario:");
        String scenarioId = unitId.substring(unitId.indexOf(':') + 1).trim();
        List<GraphRunScenario> list = add ? sp.getAddScenarios() : sp.getUpdateScenarios();
        if (list == null) {
            return null;
        }
        for (GraphRunScenario s : list) {
            if (s != null && scenarioId.equals(s.getId())) {
                return s;
            }
        }
        return null;
    }

    /**
     * 边端点既不在底图节点里，也不在本 patch 的 addNodes 里时，写入依赖错误。
     */
    private static void checkEndpointExists(
            List<String> hints,
            String unitId,
            String endpointId,
            Set<String> addNodeIdsInPatch,
            GraphJson baseGraph,
            String role) {
        if (endpointId == null || endpointId.isBlank()) {
            return;
        }
        String trimmed = endpointId.trim();
        if (addNodeIdsInPatch.contains(trimmed)) {
            return;
        }
        if (GraphLookupUtils.findNode(baseGraph != null ? baseGraph.getNodes() : null, trimmed) != null) {
            return;
        }
        hints.add("确认 " + unitId + " 的 " + role + "「" + trimmed + "」不在画布且不在本轮 addNode 中");
    }

    /**
     * 边端点落在本 patch 的 addNode 上、且该节点尚未确认时，写入「须先确认该 addNode」提示。
     */
    private static void checkAddNodeDependency(
            List<String> hints,
            String unitId,
            String edgeId,
            String endpointId,
            Set<String> addNodeIdsInPatch,
            Set<String> confirmedUnitIds) {
        if (endpointId == null || endpointId.isBlank()) {
            return;
        }
        String trimmed = endpointId.trim();
        if (!addNodeIdsInPatch.contains(trimmed)) {
            return;
        }
        String nodeKey = "addNode:" + trimmed;
        if (!confirmedUnitIds.contains(nodeKey)) {
            if (unitId.startsWith("addEdge:") && !edgeId.isEmpty()) {
                hints.add("确认 addEdge:" + edgeId + " 需要先确认 addNode:" + trimmed);
            } else {
                hints.add("确认 " + unitId + " 需要先确认 addNode:" + trimmed);
            }
        }
    }
}
