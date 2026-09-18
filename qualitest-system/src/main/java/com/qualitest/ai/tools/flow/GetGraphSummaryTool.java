package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 返回当前画布的节点摘要与边列表。
 * <p>
 * 节点数超过 FULL_NODE_LIMIT 时压缩：只保留计数，以及上下文指定的节点与相关边。
 * 另输出 startNodeCount（入度为 0 的节点数）与异常拓扑提示 topologyHint。
 * 另输出 mermaid（flowchart TB 正文）；压缩且无上下文节点时不写该字段。
 * 返回前按字节上限裁剪（先删 mermaid，再减 edges，再减 nodes）。
 */
public class GetGraphSummaryTool implements QualitestTool {

    /** 未压缩模式下返回全量节点摘要的上限；超出后 {@code compressed=true} */
    public static final int FULL_NODE_LIMIT = 80;

    private final FlowGraphContextResolver graphResolver;

    public GetGraphSummaryTool(FlowGraphContextResolver graphResolver) {
        this.graphResolver = graphResolver;
    }

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_GRAPH_SUMMARY.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        FlowGraphContextResolver.ResolvedGraph resolved = graphResolver.resolve(arguments, ctx);
        if (!resolved.isOk()) {
            return resolved.errorJson();
        }
        GraphJson graph = resolved.graph();
        JSONObject result = new JSONObject();
        if (graph.getNodes() == null) {
            result.put("nodeCount", 0);
            result.put("edgeCount", 0);
            result.put("nodes", new JSONArray());
            result.put("edges", new JSONArray());
            result.put("compressed", false);
            result.put("mermaid", FlowGraphMermaidSupport.toMermaid(graph));
            return ToolResultByteFit.fitGraphTopology(result, ctx.getMaxToolResultBytes());
        }
        int nodeCount = graph.getNodes().size();
        int edgeCount = graph.getEdges() != null ? graph.getEdges().size() : 0;
        result.put("nodeCount", nodeCount);
        result.put("edgeCount", edgeCount);
        boolean compressed = nodeCount > FULL_NODE_LIMIT;
        result.put("compressed", compressed);
        JSONArray nodes = new JSONArray();
        Set<String> includeNodeIds = new HashSet<>();
        if (compressed && ctx.getContextNodeIds() != null) {
            includeNodeIds.addAll(ctx.getContextNodeIds());
        }
        if (!compressed || !includeNodeIds.isEmpty()) {
            for (GraphNode node : graph.getNodes()) {
                if (node == null) {
                    continue;
                }
                if (compressed && !includeNodeIds.contains(node.getId())) {
                    continue;
                }
                nodes.add(FlowGraphSummarySupport.toNodeSummaryItem(node));
            }
        }
        if (compressed) {
            result.put("hint", "节点超过 " + FULL_NODE_LIMIT + "，仅返回计数"
                    + (includeNodeIds.isEmpty() ? "" : "与 contextNodeIds 对应节点")
                    + "；改单节点请用 get_node_detail");
        }
        result.put("nodes", nodes);
        JSONArray edges = new JSONArray();
        if (!compressed && graph.getEdges() != null) {
            for (GraphEdge edge : graph.getEdges()) {
                if (edge == null) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("id", edge.getId());
                item.put("source", edge.getSource());
                item.put("target", edge.getTarget());
                edges.add(item);
            }
        } else if (compressed && !includeNodeIds.isEmpty() && graph.getEdges() != null) {
            for (GraphEdge edge : graph.getEdges()) {
                if (edge == null) {
                    continue;
                }
                if (includeNodeIds.contains(edge.getSource()) || includeNodeIds.contains(edge.getTarget())) {
                    JSONObject item = new JSONObject();
                    item.put("id", edge.getId());
                    item.put("source", edge.getSource());
                    item.put("target", edge.getTarget());
                    edges.add(item);
                }
            }
        }
        result.put("edges", edges);
        // 入度为 0 的节点数；不为 1 时附带 topologyHint 供模型察觉叠图/无入口
        int startNodeCount = countStartNodes(graph);
        result.put("startNodeCount", startNodeCount);
        if (startNodeCount != 1 && nodeCount > 0) {
            result.put("topologyHint", startNodeCount == 0
                    ? "当前无开始节点（入度均为正）；造流或修复前请先理清拓扑"
                    : "当前有 " + startNodeCount + " 个开始节点；多次修复叠图时请先清空 Staging 或新建流");
        }
        if (ctx.getContextNodeIds() != null && !ctx.getContextNodeIds().isEmpty()) {
            result.put("contextNodeIds", ctx.getContextNodeIds());
        }
        if (!compressed) {
            result.put("mermaid", FlowGraphMermaidSupport.toMermaid(graph));
        } else if (!includeNodeIds.isEmpty()) {
            result.put("mermaid", FlowGraphMermaidSupport.toMermaid(graph, includeNodeIds));
        }
        return ToolResultByteFit.fitGraphTopology(result, ctx.getMaxToolResultBytes());
    }

    /**
     * 统计入度为 0 的节点个数（没有被任何边指向的节点，即开始节点候选）。
     */
    private static int countStartNodes(GraphJson graph) {
        if (graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return 0;
        }
        Set<String> targets = new HashSet<>();
        if (graph.getEdges() != null) {
            for (GraphEdge edge : graph.getEdges()) {
                if (edge != null && edge.getTarget() != null && !edge.getTarget().isBlank()) {
                    targets.add(edge.getTarget().trim());
                }
            }
        }
        int count = 0;
        for (GraphNode node : graph.getNodes()) {
            if (node == null || node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            if (!targets.contains(node.getId().trim())) {
                count++;
            }
        }
        return count;
    }
}
