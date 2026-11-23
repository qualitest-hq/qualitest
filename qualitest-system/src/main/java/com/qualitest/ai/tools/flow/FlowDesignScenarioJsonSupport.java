package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.Map;

/**
 * 运行场景只读摘要 JSON 组装。
 * 输出 id/name/环境/备注、inSeed 键名列表（不含明文），以及 maxSteps。
 */
final class FlowDesignScenarioJsonSupport {

    private FlowDesignScenarioJsonSupport() {
    }

    /** 列表与详情共用的场景摘要字段。 */
    static JSONObject toSummary(GraphRunScenario scenario) {
        JSONObject item = new JSONObject();
        item.put("id", scenario.getId());
        item.put("name", scenario.getName() != null ? scenario.getName() : "");
        String envId = scenario.getEnvId() != null && !scenario.getEnvId().isBlank()
                ? scenario.getEnvId().trim() : "";
        item.put("envId", envId);
        item.put("remark", scenario.getRemark() != null ? scenario.getRemark() : "");
        Map<String, Object> inSeed = scenario.getInSeed() != null ? scenario.getInSeed() : Map.of();
        JSONArray inSeedKeys = new JSONArray();
        inSeedKeys.addAll(inSeed.keySet());
        item.put("inSeedKeys", inSeedKeys);
        if (scenario.getMaxSteps() != null) {
            item.put("maxSteps", scenario.getMaxSteps());
        }
        return item;
    }
}