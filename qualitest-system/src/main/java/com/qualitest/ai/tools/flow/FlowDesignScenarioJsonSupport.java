package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphRunScenario;

/**
 * 运行场景只读摘要 JSON 组装。
 * 输出 id/name/环境/备注，以及 flowSeed 的键名列表（不含密钥明文）。
 */
final class FlowDesignScenarioJsonSupport {

    private FlowDesignScenarioJsonSupport() {
    }

    /** 列表与详情共用的场景摘要字段。 */
    static JSONObject toSummary(GraphRunScenario scenario) {
        JSONObject item = new JSONObject();
        item.put("id", scenario.getId());
        item.put("name", scenario.getName() != null ? scenario.getName() : "");
        item.put("testProjectEnvId",
                scenario.getTestProjectEnvId() != null ? scenario.getTestProjectEnvId() : "");
        item.put("remark", scenario.getRemark() != null ? scenario.getRemark() : "");
        JSONArray flowSeedKeys = new JSONArray();
        if (scenario.getFlowSeed() != null) {
            flowSeedKeys.addAll(scenario.getFlowSeed().keySet());
        }
        item.put("flowSeedKeys", flowSeedKeys);
        return item;
    }
}
