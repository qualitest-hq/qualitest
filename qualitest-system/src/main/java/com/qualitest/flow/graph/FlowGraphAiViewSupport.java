package com.qualitest.flow.graph;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 面向大模型工具回执的图数据清洗：去掉节点上的画布坐标字段。
 * <p>
 * 只改即将返回给模型的 JSON 视图；库内持久化的 graph_json 仍保留 position，本类不写库。
 */
public final class FlowGraphAiViewSupport {

    private FlowGraphAiViewSupport() {
    }

    /**
     * 从已解析的 graph JSON 对象中删除每个节点的 position 字段。
     * 入参不是 JSONObject、没有 nodes、或 nodes 为空时直接返回。
     *
     * @param graphObj 图根对象（通常含 nodes 数组）
     */
    public static void stripPositionsFromGraphJsonObject(Object graphObj) {
        if (!(graphObj instanceof JSONObject root)) {
            return;
        }
        JSONArray nodes = root.getJSONArray("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node != null) {
                node.remove("position");
            }
        }
    }
}
