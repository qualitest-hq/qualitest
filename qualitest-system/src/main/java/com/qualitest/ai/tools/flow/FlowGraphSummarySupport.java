package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.validate.FlowNodeType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试流画布图摘要的公共组装逻辑，供 AI 只读工具输出紧凑 JSON。
 * <p>
 * 将 {@link GraphNode}、{@link GraphEdge} 转为模型易读的字段集合，避免在多个 Tool 中重复拼装。
 */
public final class FlowGraphSummarySupport {

    private FlowGraphSummarySupport() {
    }

    /**
     * 将单个画布节点转为摘要对象。
     * <p>
     * 固定包含 {@code id}、{@code type}、{@code name}；HTTP / 子流节点再附加
     * {@code callMode}、{@code subflowId}、{@code summary} 等便于 Agent 识别的字段。
     *
     * @param node 画布节点，可为 null（返回空对象）
     * @return 节点摘要 JSON
     */
    public static JSONObject toNodeSummaryItem(GraphNode node) {
        JSONObject item = new JSONObject();
        if (node == null) {
            return item;
        }
        item.put("id", node.getId());
        item.put("type", node.getType());
        Object name = node.getData() != null ? node.getData().get("name") : null;
        item.put("name", name != null ? String.valueOf(name) : "");
        enrichNodeSummary(node, item);
        return item;
    }

    /**
     * 按节点类型向摘要对象写入扩展字段。
     * <ul>
     *   <li>HTTP：{@code callMode}（project / external）</li>
     *   <li>子流：{@code subflowId}</li>
     *   <li>任意类型：非空 {@code summary}（画布卡片副标题）</li>
     * </ul>
     *
     * @param node 源节点
     * @param item 待填充的摘要对象（通常由 {@link #toNodeSummaryItem} 创建）
     */
    public static void enrichNodeSummary(GraphNode node, JSONObject item) {
        if (node == null || node.getData() == null || item == null) {
            return;
        }
        Map<String, Object> data = node.getData();
        if (FlowNodeType.HTTP.matches(node.getType())) {
            Object callMode = data.get("callMode");
            if (callMode != null && !String.valueOf(callMode).isBlank()) {
                item.put("callMode", String.valueOf(callMode).trim());
            }
        }
        if (FlowNodeType.SUBFLOW.matches(node.getType())) {
            Object subflowId = data.get("subflowId");
            if (subflowId != null && !String.valueOf(subflowId).isBlank()) {
                item.put("subflowId", String.valueOf(subflowId).trim());
            }
        }
        Object summary = data.get("summary");
        if (summary != null && !String.valueOf(summary).isBlank()) {
            item.put("summary", String.valueOf(summary));
        }
    }

    /**
     * 统计图中各 {@code type} 的节点数量。
     * <p>
     * 键为节点类型字符串（如 http、subflow），值为出现次数；保持插入顺序便于阅读。
     *
     * @param graph 测试流图，nodes 为空时返回空对象
     * @return 类型 → 计数 的 JSON 对象
     */
    public static JSONObject buildNodeTypeCounts(GraphJson graph) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (graph != null && graph.getNodes() != null) {
            for (GraphNode node : graph.getNodes()) {
                if (node == null || node.getType() == null) {
                    continue;
                }
                String type = node.getType();
                counts.merge(type, 1, Integer::sum);
            }
        }
        JSONObject out = new JSONObject();
        counts.forEach(out::put);
        return out;
    }

    /**
     * 将边列表转为 {@code [{id, source, target}, ...]} 数组。
     *
     * @param edges 画布边列表，可为 null
     * @return 边摘要数组，跳过 null 边
     */
    public static JSONArray buildEdgeArray(List<GraphEdge> edges) {
        JSONArray arr = new JSONArray();
        if (edges == null) {
            return arr;
        }
        for (GraphEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("id", edge.getId());
            item.put("source", edge.getSource());
            item.put("target", edge.getTarget());
            arr.add(item);
        }
        return arr;
    }
}
