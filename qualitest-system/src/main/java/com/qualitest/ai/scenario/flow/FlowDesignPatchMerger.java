package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.flow.graph.ConditionBranchMergeHelper;
import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.http.FlowHttpNodePathSupport;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将 AI 增量 patch 合并进基准图副本。
 * <p>
 * 在内存中操作图结构，不写库。合并顺序：新增节点 → 更新节点 → 新增边 → 更新边 →
 * 按 suggestedDeletes 删节点/边（删节点时级联移除关联边）→ 合并 scenarioPatch 到 meta。
 * condition 出边增删改时会同步源节点 data.branches[].target。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>全量合并：acceptedIds 为 null，应用 patch 中全部增量项</li>
 *   <li>部分合并：仅应用 Diff 勾选项（addNode:、updateNode:、deleteEdge: 等键）</li>
 * </ul>
 */
@Component
public class FlowDesignPatchMerger {

    /**
     * 全量合并：将 patch 中全部增量项写入基准图副本。
     *
     * @param baseGraph 当前画布 graph_json
     * @param patch     AI 返回的增量建议
     * @param warnings  合并过程收集的告警（如场景删除被拦截）
     * @return 合并后的图副本，原 baseGraph 不被修改
     */
    public GraphJson mergeAll(GraphJson baseGraph, FlowDesignPatch patch, List<String> warnings) {
        return merge(baseGraph, patch, null, warnings);
    }

    /**
     * 按勾选项部分合并：先过滤 patch，再写入基准图副本。
     *
     * @param acceptedIds Diff 勾选项集合；null 表示不过滤，等同全量合并
     */
    public GraphJson merge(GraphJson baseGraph, FlowDesignPatch patch, Set<String> acceptedIds, List<String> warnings) {
        FlowDesignPatch effective = acceptedIds == null
                ? patch
                : filterPatchByAccepted(patch, acceptedIds);
        return mergePatch(baseGraph, effective, warnings);
    }

    /**
     * 按 Diff 勾选项从 patch 中筛出有效增量，构造独立副本。
     * <p>
     * 勾选项键名规则：addNode:{id}、updateNode:{id}、addEdge:{id}、updateEdge:{id}、
     * deleteNode:{id}、deleteEdge:{id}、scenario:activeScenarioId、addScenario:{id}、
     * updateScenario:{id}、deleteScenario:{id}。
     * acceptedIds 为空时返回空 patch（各列表为空）。
     */
    public FlowDesignPatch filterPatchByAccepted(FlowDesignPatch patch, Set<String> acceptedIds) {
        if (patch == null || acceptedIds == null || acceptedIds.isEmpty()) {
            return emptyPatch();
        }
        FlowDesignPatch filtered = new FlowDesignPatch();
        filtered.setSummary(patch.getSummary());

        // 节点与边的增删改项
        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                if (node != null && node.getId() != null && acceptedIds.contains("addNode:" + node.getId())) {
                    filtered.getAddNodes().add(node);
                }
            }
        }
        if (patch.getUpdateNodes() != null) {
            for (GraphNode node : patch.getUpdateNodes()) {
                if (node != null && node.getId() != null && acceptedIds.contains("updateNode:" + node.getId())) {
                    filtered.getUpdateNodes().add(node);
                }
            }
        }
        if (patch.getAddEdges() != null) {
            for (GraphEdge edge : patch.getAddEdges()) {
                if (edge != null && edge.getId() != null && acceptedIds.contains("addEdge:" + edge.getId())) {
                    filtered.getAddEdges().add(edge);
                }
            }
        }
        if (patch.getUpdateEdges() != null) {
            for (GraphEdge edge : patch.getUpdateEdges()) {
                if (edge != null && edge.getId() != null && acceptedIds.contains("updateEdge:" + edge.getId())) {
                    filtered.getUpdateEdges().add(edge);
                }
            }
        }

        FlowDesignPatch.SuggestedDeletes deletes = patch.getSuggestedDeletes();
        if (deletes != null) {
            if (deletes.getNodeIds() != null) {
                for (String nodeId : deletes.getNodeIds()) {
                    if (nodeId != null && acceptedIds.contains("deleteNode:" + nodeId)) {
                        filtered.getSuggestedDeletes().getNodeIds().add(nodeId);
                    }
                }
            }
            if (deletes.getEdgeIds() != null) {
                for (String edgeId : deletes.getEdgeIds()) {
                    if (edgeId != null && acceptedIds.contains("deleteEdge:" + edgeId)) {
                        filtered.getSuggestedDeletes().getEdgeIds().add(edgeId);
                    }
                }
            }
        }

        // 运行场景 meta 增量（activeScenarioId / add|update|delete scenario）
        FlowDesignScenarioPatch scenarioPatch = patch.getScenarioPatch();
        if (FlowDesignScenarioPatch.hasChanges(scenarioPatch)) {
            FlowDesignScenarioPatch filteredScenario = new FlowDesignScenarioPatch();
            if (scenarioPatch.getActiveScenarioId() != null
                    && acceptedIds.contains("scenario:activeScenarioId")) {
                filteredScenario.setActiveScenarioId(scenarioPatch.getActiveScenarioId());
            }
            if (scenarioPatch.getAddScenarios() != null) {
                for (GraphRunScenario scenario : scenarioPatch.getAddScenarios()) {
                    if (scenario != null && scenario.getId() != null
                            && acceptedIds.contains("addScenario:" + scenario.getId())) {
                        filteredScenario.getAddScenarios().add(scenario);
                    }
                }
            }
            if (scenarioPatch.getUpdateScenarios() != null) {
                for (GraphRunScenario scenario : scenarioPatch.getUpdateScenarios()) {
                    if (scenario != null && scenario.getId() != null
                            && acceptedIds.contains("updateScenario:" + scenario.getId())) {
                        filteredScenario.getUpdateScenarios().add(scenario);
                    }
                }
            }
            if (scenarioPatch.getDeleteScenarioIds() != null) {
                for (String scenarioId : scenarioPatch.getDeleteScenarioIds()) {
                    if (scenarioId != null && acceptedIds.contains("deleteScenario:" + scenarioId)) {
                        filteredScenario.getDeleteScenarioIds().add(scenarioId);
                    }
                }
            }
            if (FlowDesignScenarioPatch.hasChanges(filteredScenario)) {
                filtered.setScenarioPatch(filteredScenario);
            }
        }

        return filtered;
    }

    /** 构造各列表均为空的 patch，用于无勾选项时的过滤结果 */
    private static FlowDesignPatch emptyPatch() {
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setSuggestedDeletes(new FlowDesignPatch.SuggestedDeletes());
        return patch;
    }

    /**
     * 将有效 patch 逐项写入图副本。
     * 已存在 id 的 add 操作跳过；update 目标不存在时跳过。
     */
    private GraphJson mergePatch(GraphJson baseGraph, FlowDesignPatch patch, List<String> warnings) {
        GraphJson graph = baseGraph == null ? GraphJson.builder().build() : baseGraph.copy();
        List<GraphNode> nodes = graph.getNodes();
        List<GraphEdge> edges = graph.getEdges();

        if (patch.getAddNodes() != null) {
            for (GraphNode node : patch.getAddNodes()) {
                if (node == null || node.getId() == null || node.getId().isBlank()) {
                    continue;
                }
                if (GraphLookupUtils.findNode(nodes, node.getId()) != null) {
                    continue;
                }
                nodes.add(node);
            }
        }

        if (patch.getUpdateNodes() != null) {
            for (GraphNode update : patch.getUpdateNodes()) {
                if (update == null || update.getId() == null || update.getId().isBlank()) {
                    continue;
                }
                GraphNode existing = GraphLookupUtils.findNode(nodes, update.getId());
                if (existing == null) {
                    continue;
                }
                applyNodeUpdate(existing, update);
            }
        }

        if (patch.getAddEdges() != null) {
            for (GraphEdge edge : patch.getAddEdges()) {
                if (edge == null || edge.getId() == null || edge.getId().isBlank()) {
                    continue;
                }
                if (GraphLookupUtils.findEdge(edges, edge.getId()) != null) {
                    continue;
                }
                edges.add(edge);
                ConditionBranchMergeHelper.syncConditionEdgeToNodes(nodes, edges, edge);
            }
        }

        if (patch.getUpdateEdges() != null) {
            for (GraphEdge update : patch.getUpdateEdges()) {
                if (update == null || update.getId() == null || update.getId().isBlank()) {
                    continue;
                }
                GraphEdge existing = GraphLookupUtils.findEdge(edges, update.getId());
                if (existing == null) {
                    continue;
                }
                applyEdgeUpdate(existing, update);
                ConditionBranchMergeHelper.syncConditionEdgeToNodes(nodes, edges, existing);
            }
        }

        FlowDesignPatch.SuggestedDeletes deletes = patch.getSuggestedDeletes();
        if (deletes != null) {
            Set<String> deleteNodeIds = new HashSet<>();
            if (deletes.getNodeIds() != null) {
                deleteNodeIds.addAll(deletes.getNodeIds());
            }
            if (!deleteNodeIds.isEmpty()) {
                nodes.removeIf(n -> n != null && deleteNodeIds.contains(n.getId()));
                edges.removeIf(e -> e != null
                        && (deleteNodeIds.contains(e.getSource()) || deleteNodeIds.contains(e.getTarget())));
            }
            Set<String> deleteEdgeIds = new HashSet<>();
            if (deletes.getEdgeIds() != null) {
                deleteEdgeIds.addAll(deletes.getEdgeIds());
            }
            if (!deleteEdgeIds.isEmpty()) {
                for (GraphEdge edge : edges) {
                    if (edge != null && deleteEdgeIds.contains(edge.getId())) {
                        ConditionBranchMergeHelper.clearConditionTargetForRemovedEdge(nodes, edge);
                    }
                }
                edges.removeIf(e -> e != null && deleteEdgeIds.contains(e.getId()));
            }
        }

        FlowDesignScenarioPatch scenarioPatch = patch.getScenarioPatch();
        if (FlowDesignScenarioPatch.hasChanges(scenarioPatch)) {
            GraphMeta meta = graph.getMeta();
            if (meta == null) {
                meta = new GraphMeta();
                graph.setMeta(meta);
            }
            GraphMeta merged = ScenarioPatchMerger.merge(meta, scenarioPatch, warnings);
            meta.setActiveScenarioId(merged.getActiveScenarioId());
            meta.setScenarios(merged.getScenarios());
        }

        return graph;
    }

    /** 在节点列表中按 id 查找，未找到返回 null */
    static GraphNode findNode(List<GraphNode> nodes, String id) {
        return GraphLookupUtils.findNode(nodes, id);
    }

    /** 在边列表中按 id 查找，未找到返回 null */
    static GraphEdge findEdge(List<GraphEdge> edges, String id) {
        return GraphLookupUtils.findEdge(edges, id);
    }

    /**
     * 更新已有节点：非空 type/position 覆盖；data 做浅合并。
     * project HTTP 合并后删除整份 requestConfig、临时 requestBody、apiPath。
     */
    static void applyNodeUpdate(GraphNode existing, GraphNode update) {
        if (update.getType() != null && !update.getType().isBlank()) {
            existing.setType(update.getType());
        }
        if (update.getPosition() != null) {
            existing.setPosition(update.getPosition());
        }
        if (update.getData() != null && !update.getData().isEmpty()) {
            Map<String, Object> merged = new HashMap<>();
            if (existing.getData() != null) {
                merged.putAll(existing.getData());
            }
            merged.putAll(update.getData());
            stripProjectHttpThickFields(update.getType() != null ? update.getType() : existing.getType(), merged);
            existing.setData(merged);
        }
    }

    /**
     * project HTTP 节点：删除整份 requestConfig、临时 requestBody、apiPath。
     * external 模式不动。
     */
    static void stripProjectHttpThickFields(String type, Map<String, Object> data) {
        if (data == null) {
            return;
        }
        if (type != null && !type.isBlank() && !"http".equals(type)) {
            return;
        }
        Object callModeObj = data.get("callMode");
        String callMode = callModeObj != null ? String.valueOf(callModeObj).trim() : "";
        if (FlowHttpCallMode.isExternal(callMode)) {
            return;
        }
        data.remove("requestConfig");
        data.remove("requestBody");
        FlowHttpNodePathSupport.stripNodeApiPath(data);
    }

    /** 将 update 中非空字段覆盖到已有边 */
    static void applyEdgeUpdate(GraphEdge existing, GraphEdge update) {
        if (update.getSource() != null && !update.getSource().isBlank()) {
            existing.setSource(update.getSource());
        }
        if (update.getTarget() != null && !update.getTarget().isBlank()) {
            existing.setTarget(update.getTarget());
        }
        if (update.getLabel() != null) {
            existing.setLabel(update.getLabel());
        }
    }

    /** 深拷贝 graph_json，避免修改调用方持有的基准图 */
    static GraphJson cloneGraph(GraphJson base) {
        if (base == null) {
            return GraphJson.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build();
        }
        GraphJson cloned = GraphJson.parse(JSON.toJSONString(base));
        if (cloned.getNodes() == null) {
            cloned.setNodes(new ArrayList<>());
        }
        if (cloned.getEdges() == null) {
            cloned.setEdges(new ArrayList<>());
        }
        return cloned;
    }
}
