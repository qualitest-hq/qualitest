package com.qualitest.ai.tools.flow;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphSchemaVersions;
import com.qualitest.flow.validate.FlowNodeType;

/**
 * 组装新建测试流用的空画布 graph_json。
 * <p>
 * 默认带一个 scenario-entry，冒烟场景挂在入口 data.scenarios（含空 inSeed）；
 * meta 仅视口、排版、schemaVersion、startNodeId。
 */
public final class EmptyTestFlowGraphFactory {

    private EmptyTestFlowGraphFactory() {
    }

    /**
     * 空 edges，一个默认场景入口 + 冒烟场景；meta 仅视口/排版。
     *
     * @return 可直接写入 test_flow.graph_json 的 JSON 字符串
     */
    public static String createEmptyGraphJson() {
        String scenarioId = String.valueOf(IdUtil.getSnowflakeNextId());
        String entryId = String.valueOf(IdUtil.getSnowflakeNextId());

        JSONObject scenario = new JSONObject();
        scenario.put("id", scenarioId);
        scenario.put("name", "默认（冒烟）");
        scenario.put("envId", "");
        scenario.put("inSeed", new JSONObject());
        scenario.put("remark", "");

        JSONArray scenarios = new JSONArray();
        scenarios.add(scenario);

        JSONObject entryData = new JSONObject();
        entryData.put("doc", "");
        entryData.put("scenarios", scenarios);

        JSONObject entryPos = new JSONObject();
        entryPos.put("x", 0);
        entryPos.put("y", 0);

        JSONObject entryNode = new JSONObject();
        entryNode.put("id", entryId);
        entryNode.put("type", FlowNodeType.SCENARIO_ENTRY.getCode());
        entryNode.put("name", "默认场景入口");
        entryNode.put("position", entryPos);
        entryNode.put("data", entryData);

        JSONArray nodes = new JSONArray();
        nodes.add(entryNode);

        JSONObject viewport = new JSONObject();
        viewport.put("x", 40);
        viewport.put("y", 40);
        viewport.put("zoom", 1);

        JSONObject meta = new JSONObject();
        meta.put("viewport", viewport);
        meta.put("layout", "default");
        meta.put("schemaVersion", GraphSchemaVersions.CURRENT);
        meta.put("startNodeId", entryId);

        JSONObject graph = new JSONObject();
        graph.put("nodes", nodes);
        graph.put("edges", new JSONArray());
        graph.put("meta", meta);
        return JSON.toJSONString(graph);
    }
}
