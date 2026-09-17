package com.qualitest.flow.sync;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 全自动 / MCP 落盘前，把本批图变更片段暂存在线程本地。
 * 写库成功发「图已提交」事件时附带这些片段，前端可增量合并；
 * 人手保存不设片段，前端走整图重拉。
 */
public final class FlowGraphCommitPatchHolder {

    private static final ThreadLocal<PatchPayload> HOLDER = new ThreadLocal<>();

    private FlowGraphCommitPatchHolder() {
    }

    /** 写入本线程片段；空则清除 */
    public static void set(PatchPayload payload) {
        if (payload == null || payload.isEmpty()) {
            HOLDER.remove();
        } else {
            HOLDER.set(payload);
        }
    }

    /** 读取本线程片段，可能为 null */
    public static PatchPayload get() {
        return HOLDER.get();
    }

    /** 清除本线程片段，避免泄漏到后续请求 */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 从本批设计 patch 与即将落盘的完整图，抽出变更节点与相关边。
     * 含：新增/更新节点与边、删除 id、以及落盘图中对应节点 JSON 与关联边 JSON。
     */
    public static PatchPayload fromCapture(FlowDesignPatch patch, GraphJson graph) {
        if (patch == null || graph == null) {
            return PatchPayload.empty();
        }
        Set<String> changedNodeIds = new LinkedHashSet<>();
        Set<String> changedEdgeIds = new LinkedHashSet<>();
        Set<String> deletedNodeIds = new LinkedHashSet<>();
        Set<String> deletedEdgeIds = new LinkedHashSet<>();

        addNodeIds(changedNodeIds, patch.getAddNodes());
        addNodeIds(changedNodeIds, patch.getUpdateNodes());
        addEdgeIds(changedEdgeIds, patch.getAddEdges());
        addEdgeIds(changedEdgeIds, patch.getUpdateEdges());
        if (patch.getSuggestedDeletes() != null) {
            addRawIds(deletedNodeIds, patch.getSuggestedDeletes().getNodeIds());
            addRawIds(deletedEdgeIds, patch.getSuggestedDeletes().getEdgeIds());
        }

        if (changedNodeIds.isEmpty() && changedEdgeIds.isEmpty()
                && deletedNodeIds.isEmpty() && deletedEdgeIds.isEmpty()) {
            return PatchPayload.empty();
        }

        // 从落盘图抽出变更节点的完整 JSON
        List<Object> nodePatches = new ArrayList<>();
        if (graph.getNodes() != null) {
            for (GraphNode node : graph.getNodes()) {
                if (node != null && node.getId() != null && changedNodeIds.contains(node.getId().trim())) {
                    nodePatches.add(JSON.parseObject(JSON.toJSONString(node)));
                }
            }
        }

        // 变更边 + 挂在变更节点上的边一并带上，方便前端局部接边
        Set<String> edgePick = new LinkedHashSet<>(changedEdgeIds);
        if (graph.getEdges() != null) {
            for (GraphEdge edge : graph.getEdges()) {
                if (edge == null) {
                    continue;
                }
                String sid = edge.getSource() != null ? edge.getSource().trim() : "";
                String tid = edge.getTarget() != null ? edge.getTarget().trim() : "";
                if (changedNodeIds.contains(sid) || changedNodeIds.contains(tid)) {
                    if (edge.getId() != null && !edge.getId().isBlank()) {
                        edgePick.add(edge.getId().trim());
                    }
                }
            }
        }

        List<Object> edgePatches = new ArrayList<>();
        if (graph.getEdges() != null) {
            for (GraphEdge edge : graph.getEdges()) {
                if (edge != null && edge.getId() != null && edgePick.contains(edge.getId().trim())) {
                    edgePatches.add(JSON.parseObject(JSON.toJSONString(edge)));
                }
            }
        }

        List<String> allChangedNodes = new ArrayList<>(changedNodeIds);
        allChangedNodes.addAll(deletedNodeIds);
        return new PatchPayload(
                List.copyOf(allChangedNodes),
                List.copyOf(nodePatches),
                List.copyOf(edgePatches),
                List.copyOf(deletedNodeIds),
                List.copyOf(deletedEdgeIds));
    }

    private static void addNodeIds(Set<String> target, List<GraphNode> nodes) {
        if (nodes == null) {
            return;
        }
        for (GraphNode n : nodes) {
            if (n != null && n.getId() != null && !n.getId().isBlank()) {
                target.add(n.getId().trim());
            }
        }
    }

    private static void addEdgeIds(Set<String> target, List<GraphEdge> edges) {
        if (edges == null) {
            return;
        }
        for (GraphEdge e : edges) {
            if (e != null && e.getId() != null && !e.getId().isBlank()) {
                target.add(e.getId().trim());
            }
        }
    }

    private static void addRawIds(Set<String> target, List<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                target.add(id.trim());
            }
        }
    }

    /**
     * 一批图变更的推送载荷。
     *
     * @param changedNodeIds 变更或删除的节点 id（前端高亮）
     * @param nodePatches    落盘图中对应节点的 JSON 对象列表
     * @param edgePatches    相关边的 JSON 对象列表
     * @param deletedNodeIds 本批删除的节点 id
     * @param deletedEdgeIds 本批删除的边 id
     */
    public record PatchPayload(
            List<String> changedNodeIds,
            List<Object> nodePatches,
            List<Object> edgePatches,
            List<String> deletedNodeIds,
            List<String> deletedEdgeIds) {

        /** 无任何变更 */
        public static PatchPayload empty() {
            return new PatchPayload(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList());
        }

        /** 是否无可推送内容 */
        public boolean isEmpty() {
            return (changedNodeIds == null || changedNodeIds.isEmpty())
                    && (nodePatches == null || nodePatches.isEmpty())
                    && (edgePatches == null || edgePatches.isEmpty())
                    && (deletedNodeIds == null || deletedNodeIds.isEmpty())
                    && (deletedEdgeIds == null || deletedEdgeIds.isEmpty());
        }
    }
}
