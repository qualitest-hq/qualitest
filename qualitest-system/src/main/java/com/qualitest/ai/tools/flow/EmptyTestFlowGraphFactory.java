package com.qualitest.ai.tools.flow;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphSchemaVersions;

/**
 * 组装与 Web「新建测试流」一致的空画布 graph_json。
 */
public final class EmptyTestFlowGraphFactory {

    private EmptyTestFlowGraphFactory() {
    }

    /**
     * 空 nodes/edges，附带默认视口与一条「默认（冒烟）」运行场景。
     *
     * @return 可直接写入 test_flow.graph_json 的 JSON 字符串
     */
    public static String createEmptyGraphJson() {
        String scenarioId = String.valueOf(IdUtil.getSnowflakeNextId());

        JSONObject scenario = new JSONObject();
        scenario.put("id", scenarioId);
        scenario.put("name", "默认（冒烟）");
        scenario.put("testProjectEnvId", "");
        scenario.put("flowSeed", new JSONObject());
        scenario.put("remark", "");

        JSONObject viewport = new JSONObject();
        viewport.put("x", 40);
        viewport.put("y", 40);
        viewport.put("zoom", 1);

        JSONArray scenarios = new JSONArray();
        scenarios.add(scenario);

        JSONObject meta = new JSONObject();
        meta.put("viewport", viewport);
        meta.put("layout", "manual");
        meta.put("schemaVersion", GraphSchemaVersions.CURRENT);
        meta.put("activeScenarioId", scenarioId);
        meta.put("scenarios", scenarios);
        meta.put("flowOutputs", new JSONArray());

        JSONObject graph = new JSONObject();
        graph.put("nodes", new JSONArray());
        graph.put("edges", new JSONArray());
        graph.put("meta", meta);
        return JSON.toJSONString(graph);
    }
}
