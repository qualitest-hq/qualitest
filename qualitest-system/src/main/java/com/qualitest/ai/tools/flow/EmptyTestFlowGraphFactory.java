package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphSchemaVersions;

/**
 * 组装新建测试流用的空画布 graph_json。
 * <p>
 * 完全空白画布：不含任何节点、边与场景；meta 仅含视口、排版与 schemaVersion。
 */
public final class EmptyTestFlowGraphFactory {

    /** 工具类，禁止实例化 */
    private EmptyTestFlowGraphFactory() {
    }

    /**
     * 生成空 nodes / edges 的画布，入口节点由使用者后续自行添加。
     *
     * @return 可直接写入 test_flow.graph_json 的 JSON 字符串
     */
    public static String createEmptyGraphJson() {
        JSONObject viewport = new JSONObject();
        viewport.put("x", 40);
        viewport.put("y", 40);
        viewport.put("zoom", 1);

        JSONObject meta = new JSONObject();
        meta.put("viewport", viewport);
        meta.put("layout", "default");
        meta.put("schemaVersion", GraphSchemaVersions.CURRENT);

        JSONObject graph = new JSONObject();
        graph.put("nodes", new JSONArray());
        graph.put("edges", new JSONArray());
        graph.put("meta", meta);
        return JSON.toJSONString(graph);
    }
}
