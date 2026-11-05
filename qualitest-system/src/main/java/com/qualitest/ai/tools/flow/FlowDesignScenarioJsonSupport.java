package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphRunScenario;

/**
 * 运行场景只读摘要 JSON 组装。
 * 输出 id/name/环境/备注、flowSeed 键名列表（不含明文），以及已配置的 maxSteps。
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
        // 已配置的单次运行步数上限；未配置则省略该字段
        if (scenario.getMaxSteps() != null) {
            item.put("maxSteps", scenario.getMaxSteps());
        }
        return item;
    }
}
