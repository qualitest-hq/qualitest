package com.qualitest.flow.graph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.http.FlowHttpCallMode;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;

import java.util.List;

/**
 * 遍历测试流 graph_json 里的 HTTP 节点。
 * <p>
 * 只读场景：统计绑定关系、做语义健康检查。<br>
 * 写场景：对已解析的根对象就地改节点 data（例如厚节点洗成薄节点）。
 */
public final class FlowHttpNodeVisitor {

    private FlowHttpNodeVisitor() {
    }

    /**
     * 每个 HTTP 节点回调一次。
     */
    @FunctionalInterface
    public interface HttpNodeConsumer {
        /**
         * @param nodeId 画布节点 id，可能为空
         * @param node   节点完整 JSON
         * @param data   节点 data，调用方保证非 null
         */
        void accept(String nodeId, JSONObject node, JSONObject data);
    }

    /**
     * 遍历已解析图根对象中的 HTTP 节点。
     * <p>
     * 判定规则：type 为空或为 http，且存在 data。可在回调里直接改 data，改动能落到同一份 root 上。
     */
    public static void visit(JSONObject root, HttpNodeConsumer consumer) {
        if (root == null || consumer == null) {
            return;
        }
        JSONArray nodes = root.getJSONArray("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node == null) {
                continue;
            }
            String type = node.getString("type");
            if (type != null && !type.isBlank() && !"http".equals(type)) {
                continue;
            }
            JSONObject data = node.getJSONObject("data");
            if (data == null) {
                continue;
            }
            consumer.accept(node.getString("id"), node, data);
        }
    }

    /**
     * 解析 graph_json 字符串后遍历 HTTP 节点。
     *
     * @return 解析失败返回错误信息；成功（含空图）返回 null
     */
    public static String visit(String graphJson, HttpNodeConsumer consumer) {
        if (consumer == null) {
            return null;
        }
        if (graphJson == null || graphJson.isBlank()) {
            return null;
        }
        JSONObject root;
        try {
            root = JSON.parseObject(graphJson);
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : "graph_json 解析失败";
        }
        visit(root, consumer);
        return null;
    }

    /**
     * 已绑定项目接口的 HTTP 节点回调。
     */
    @FunctionalInterface
    public interface ProjectBoundHttpConsumer {
        /**
         * @param node  图节点
         * @param data  节点 data 快照
         * @param apiId 已解析的 testProjectApiId
         */
        void accept(GraphNode node, JSONObject data, Long apiId);
    }

    /**
     * 遍历 GraphJson 中 project 绑定的 HTTP 节点。
     * <p>
     * 判定与 {@link #visit(JSONObject, HttpNodeConsumer)} 相同：type 为空或 http，且 {@link #isProjectBoundHttp}。
     */
    public static void visitProjectBound(GraphJson graph, ProjectBoundHttpConsumer consumer) {
        if (graph == null || consumer == null) {
            return;
        }
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
            if (!isProjectBoundHttp(data)) {
                continue;
            }
            Long apiId = parseTestProjectApiId(data.get("testProjectApiId"));
            if (apiId == null) {
                continue;
            }
            consumer.accept(node, data, apiId);
        }
    }

    /**
     * 是否按「项目接口」处理该 HTTP 节点。
     * <ul>
     *   <li>callMode = external → 否</li>
     *   <li>callMode = project → 是</li>
     *   <li>未写 callMode，但带了 testProjectApiId → 是</li>
     *   <li>其余 → 否</li>
     * </ul>
     */
    public static boolean isProjectBoundHttp(JSONObject data) {
        if (data == null) {
            return false;
        }
        String callMode = data.getString("callMode");
        if (callMode == null || callMode.isBlank()) {
            return data.get("testProjectApiId") != null;
        }
        if (FlowHttpCallMode.isExternal(callMode)) {
            return false;
        }
        return FlowHttpCallMode.isProject(callMode) || data.get("testProjectApiId") != null;
    }

    /**
     * 把节点上的 testProjectApiId 转成 Long；空串或非法数字返回 null。
     */
    public static Long parseTestProjectApiId(Object rawId) {
        if (rawId == null) {
            return null;
        }
        try {
            String text = String.valueOf(rawId).trim();
            if (text.isEmpty()) {
                return null;
            }
            return Long.parseLong(text);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 节点展示名：优先 data.name，其次 data.apiName，最后用 nodeId。
     */
    public static String resolveNodeName(JSONObject data, String nodeId) {
        if (data != null) {
            String name = data.getString("name");
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
            String apiName = data.getString("apiName");
            if (apiName != null && !apiName.isBlank()) {
                return apiName.trim();
            }
        }
        return nodeId != null ? nodeId : "";
    }
}
