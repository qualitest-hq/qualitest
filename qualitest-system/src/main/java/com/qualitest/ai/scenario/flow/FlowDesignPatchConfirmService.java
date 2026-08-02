package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmRequest;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmResult;
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
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.mapper.TestProjectApiMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 设计 patch 的单 Staging 单元确认服务。
 * <p>
 * 用户在画布上对单个变更单元点击「确认」后，本服务在服务端完成：
 * <ol>
 *   <li>draftOverride 合并进 patch 对应项</li>
 *   <li>patch 字段规范化</li>
 *   <li>依赖校验（如 addEdge 须先 confirm 端点 addNode）</li>
 *   <li>按 unitId 过滤出单单元增量子集并合并到 graph_json 副本</li>
 *   <li>运行全图结构校验，汇总 errors 与 warnings</li>
 *   <li>用上游接口响应示例试算 assert/condition 的 http.body 左值；未命中则确认失败</li>
 * </ol>
 * 不写库；成功时返回 graphJson 供前端落盘并清除 Staging 标记。
 */
@Service
@RequiredArgsConstructor
public class FlowDesignPatchConfirmService {

    private static final Logger log = LoggerFactory.getLogger(FlowDesignPatchConfirmService.class);

    private final FlowDesignPatchNormalizer patchNormalizer;
    private final FlowDesignPatchMerger patchMerger;
    private final GraphJsonValidator graphJsonValidator;
    private final TestProjectApiMapper testProjectApiMapper;

    /**
     * 确认单个 Staging 单元。
     *
     * @param request 当前 graph_json、完整 patch、unitId、可选 draft 与已确认单元列表
     * @return 校验摘要 + 确认后的 graph_json；ok=false 时 graphJson 为 null
     */
    public FlowDesignPatchConfirmResult confirmUnit(FlowDesignPatchConfirmRequest request) {
        long startedAt = System.nanoTime();
        List<String> warnings = new ArrayList<>();

        if (request == null || request.getPatch() == null) {
            return failureResult(List.of("patch 不能为空"), warnings, List.of(), "");
        }

        String unitId = normalizeUnitId(request.getUnitId());
        if (unitId == null) {
            return failureResult(List.of("unitId 不能为空"), warnings, List.of(), "");
        }

        GraphJson baseGraph = request.getGraphJson() != null
                ? request.getGraphJson()
                : GraphJson.builder().build();
        String baseGraphHash = GraphJsonHashUtil.computeBaseGraphHash(baseGraph);

        FlowDesignPatch patch = JSON.parseObject(JSON.toJSONString(request.getPatch()), FlowDesignPatch.class);
        applyDraftOverride(patch, unitId, request.getDraftOverride());

        patchNormalizer.preparePatch(patch, baseGraph, request.getTestProjectId(), warnings);

        GraphJson workingGraph = FlowDesignPatchMerger.cloneGraph(baseGraph);
        applyUnitDraftToGraph(workingGraph, patch, unitId, request.getDraftOverride());

        Set<String> confirmedUnitIds = normalizeConfirmedUnitIds(request.getConfirmedUnitIds());
        Set<String> rejectedUnitIds = normalizeConfirmedUnitIds(request.getRejectedUnitIds());
        List<String> dependencyHints = computeDependencyHints(patch, unitId, confirmedUnitIds);
        if (!dependencyHints.isEmpty()) {
            List<String> errors = new ArrayList<>(dependencyHints);
            logConfirmMetrics(startedAt, unitId, false);
            return failureResult(errors, warnings, dependencyHints, baseGraphHash);
        }

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

        // 设计期门禁：上游接口响应示例上试算 http.body 左值，空/[] 则不允许 Staging 确认
        List<String> assertGateErrors = AssertPathDesignGate.validate(merged, this::loadApi);
        List<String> allErrors = new ArrayList<>(validation.getErrors());
        allErrors.addAll(assertGateErrors);
        boolean ok = allErrors.isEmpty();

        logConfirmMetrics(startedAt, unitId, ok);

        if (!ok) {
            return FlowDesignPatchConfirmResult.builder()
                    .ok(false)
                    .errors(allErrors)
                    .warnings(allWarnings)
                    .graphJson(null)
                    .dependencyHints(List.of())
                    .baseGraphHash(baseGraphHash)
                    .build();
        }

        return FlowDesignPatchConfirmResult.builder()
                .ok(true)
                .errors(List.of())
                .warnings(allWarnings)
                .graphJson(merged)
                .dependencyHints(List.of())
                .baseGraphHash(baseGraphHash)
                .build();
    }

    /** 按 id 加载项目接口；mapper 为空或 id 为空时返回 null（门禁将跳过该节点） */
    private TestProjectApi loadApi(Long apiId) {
        if (apiId == null || testProjectApiMapper == null) {
            return null;
        }
        return testProjectApiMapper.selectTestProjectApiById(apiId);
    }

    private static FlowDesignPatchConfirmResult failureResult(
            List<String> errors,
            List<String> warnings,
            List<String> dependencyHints,
            String baseGraphHash) {
        return FlowDesignPatchConfirmResult.builder()
                .ok(false)
                .errors(errors)
                .warnings(warnings)
                .graphJson(null)
                .dependencyHints(dependencyHints)
                .baseGraphHash(baseGraphHash)
                .build();
    }

    private void logConfirmMetrics(long startedAt, String unitId, boolean ok) {
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.debug("ai_patch_confirm_ms={} unit_id={} ok={}", elapsedMs, unitId, ok);
    }

    private static String normalizeUnitId(String unitId) {
        if (unitId == null || unitId.isBlank()) {
            return null;
        }
        return unitId.trim();
    }

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
     * 将前端 draft 合并进 patch 中 unitId 对应的节点/边/场景项。
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

        if ("scenario:activeScenarioId".equals(unitId) && draft.containsKey("activeScenarioId")) {
            FlowDesignScenarioPatch scenarioPatch = patch.getScenarioPatch();
            if (scenarioPatch == null) {
                scenarioPatch = new FlowDesignScenarioPatch();
                patch.setScenarioPatch(scenarioPatch);
            }
            scenarioPatch.setActiveScenarioId(draft.getString("activeScenarioId"));
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
            // draft 可能冲掉 preparePatch 补全；按 type 再规范化一次
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
     * 确认 addEdge 时，若端点为 patch 内 addNode 且尚未 confirm，返回人类可读提示并阻断合并。
     */
    private List<String> computeDependencyHints(
            FlowDesignPatch patch,
            String unitId,
            Set<String> confirmedUnitIds) {
        List<String> hints = new ArrayList<>();
        if (!unitId.startsWith("addEdge:") || patch.getAddEdges() == null) {
            return hints;
        }

        String edgeId = unitId.substring("addEdge:".length());
        GraphEdge edge = null;
        for (GraphEdge candidate : patch.getAddEdges()) {
            if (candidate != null && edgeId.equals(candidate.getId())) {
                edge = candidate;
                break;
            }
        }
        if (edge == null) {
            return hints;
        }

        Set<String> addNodeIdsInPatch = new HashSet<>();
        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                if (node != null && node.getId() != null && !node.getId().isBlank()) {
                    addNodeIdsInPatch.add(node.getId().trim());
                }
            }
        }

        checkAddNodeDependency(hints, edgeId, edge.getSource(), addNodeIdsInPatch, confirmedUnitIds);
        checkAddNodeDependency(hints, edgeId, edge.getTarget(), addNodeIdsInPatch, confirmedUnitIds);
        return hints;
    }

    private static void checkAddNodeDependency(
            List<String> hints,
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
            hints.add("确认 addEdge:" + edgeId + " 需要先确认 addNode:" + trimmed);
        }
    }
}
