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
 * 返回前按拓扑结果形状做字节上限裁剪（先清空 edges，再减 nodes）。
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
        if (ctx.getContextNodeIds() != null && !ctx.getContextNodeIds().isEmpty()) {
            result.put("contextNodeIds", ctx.getContextNodeIds());
        }
        return ToolResultByteFit.fitGraphTopology(result, ctx.getMaxToolResultBytes());
    }
}
