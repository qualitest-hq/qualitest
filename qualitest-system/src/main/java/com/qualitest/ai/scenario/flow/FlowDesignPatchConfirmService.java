package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmRequest;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatchConfirmResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 单条 Staging 单元确认：用户点 ✓ 后在服务端合并该单元，不写库。
 * <p>
 * 流程：合并 draft → 规范化 patch（旁路文案进 mergeNotes）→ 按 unitId 合入图副本。<br>
 * 依赖顺序 / 图结构 / 断言路径<strong>不挡确认</strong>：合并后的问题由画布图 error 展示，开跑再硬拦。<br>
 * 鉴权/必填由画布 {@code validate?stage=canvas} 图 error 刷新，不进本响应。<br>
 * 请求合法时始终返回 graphJson，由前端写回编辑态并清 Staging 标记。
 */
@Service
@RequiredArgsConstructor
public class FlowDesignPatchConfirmService {

    private static final Logger log = LoggerFactory.getLogger(FlowDesignPatchConfirmService.class);

    private final FlowDesignPatchNormalizer patchNormalizer;
    private final FlowDesignPatchMerger patchMerger;

    /**
     * 确认单个 Staging 单元。
     *
     * @param request 当前图、patch、unitId、可选 draft
     * @return 必带 graphJson + baseGraphHash
     * @throws ServiceException patch / unitId 非法时
     */
    public FlowDesignPatchConfirmResult confirmUnit(FlowDesignPatchConfirmRequest request) {
        long startedAt = System.nanoTime();

        if (request == null || request.getPatch() == null) {
            throw new ServiceException("patch 不能为空");
        }

        String unitId = normalizeUnitId(request.getUnitId());
        if (unitId == null) {
            throw new ServiceException("unitId 不能为空");
        }

        GraphJson baseGraph = request.getGraphJson() != null
                ? request.getGraphJson()
                : GraphJson.builder().build();
        String baseGraphHash = GraphJsonHashUtil.computeBaseGraphHash(baseGraph);

        FlowDesignPatch patch = JSON.parseObject(JSON.toJSONString(request.getPatch()), FlowDesignPatch.class);
        applyDraftOverride(patch, unitId, request.getDraftOverride());

        // prepare 文案只进合并旁路，不挡落盘、不回传确认响应
        List<String> mergeNotes = new ArrayList<>();
        patchNormalizer.preparePatch(patch, baseGraph, request.getTestProjectId(), mergeNotes, mergeNotes);

        GraphJson workingGraph = FlowDesignPatchMerger.cloneGraph(baseGraph);
        applyUnitDraftToGraph(workingGraph, patch, unitId, request.getDraftOverride());

        Set<String> acceptedIds = Set.of(unitId);
        GraphJson merged = patchMerger.merge(workingGraph, patch, acceptedIds, mergeNotes);

        logConfirmMetrics(startedAt, unitId);
        return FlowDesignPatchConfirmResult.builder()
                .graphJson(merged)
                .baseGraphHash(baseGraphHash)
                .build();
    }

    private void logConfirmMetrics(long startedAt, String unitId) {
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.debug("ai_patch_confirm_ms={} unit_id={}", elapsedMs, unitId);
    }

    /** 空白 unitId 视为无效，返回 null */
    private static String normalizeUnitId(String unitId) {
        if (unitId == null || unitId.isBlank()) {
            return null;
        }
        return unitId.trim();
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
                    // 写入 remark、env、inSeed 等场景字段（失败策略 / maxSteps 已迁出场景）
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

}
