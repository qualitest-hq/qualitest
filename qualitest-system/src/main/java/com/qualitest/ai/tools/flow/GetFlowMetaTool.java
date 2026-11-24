package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.context.EntryScenarioSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.List;
import java.util.Map;

/**
 * 读取当前测试流画布元数据与场景摘要。
 * <p>
 * 回执按全部 scenario-entry 列举；单入口时额外回填顶层 entryNodeId / scenarios / activeScenarioId 以便兼容。
 */
public class GetFlowMetaTool implements QualitestTool {

    private final FlowGraphContextResolver graphResolver;

    public GetFlowMetaTool(FlowGraphContextResolver graphResolver) {
        this.graphResolver = graphResolver;
    }

    /** @return 工具 id */
    @Override
    public String getName() {
        return FlowDesignToolNames.GET_FLOW_META.getId();
    }

    /**
     * 组装各入口场景摘要 JSON；解析失败时返回错误回执。
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        FlowGraphContextResolver.ResolvedGraph resolved = graphResolver.resolve(arguments, ctx);
        if (!resolved.isOk()) {
            return resolved.errorJson();
        }
        GraphJson graph = resolved.graph();
        JSONObject result = new JSONObject();
        result.put("activeScenarioId", "");
        result.put("scenarios", new JSONArray());
        result.put("entryNodeId", "");

        List<GraphNode> entryNodes = EntryScenarioSupport.listScenarioEntries(graph);
        JSONArray entriesArr = new JSONArray();
        for (GraphNode entry : entryNodes) {
            if (entry == null) {
                continue;
            }
            JSONObject entryObj = new JSONObject();
            String entryId = entry.getId() != null ? entry.getId() : "";
            entryObj.put("entryNodeId", entryId);
            entryObj.put("entryName", entry.getName() != null ? entry.getName() : "");
            JSONArray scenarioArr = toScenarioSummaries(EntryScenarioSupport.scenariosOf(entry));
            entryObj.put("scenarios", scenarioArr);
            entriesArr.add(entryObj);
        }
        result.put("entries", entriesArr);

        // 单入口：顶层字段与旧调用方兼容（直接复用 entries[0]，避免再算一遍）
        if (entriesArr.size() == 1) {
            JSONObject sole = entriesArr.getJSONObject(0);
            result.put("entryNodeId", sole.getString("entryNodeId"));
            JSONArray scenarios = sole.getJSONArray("scenarios");
            result.put("scenarios", scenarios != null ? scenarios : new JSONArray());
            if (scenarios != null && !scenarios.isEmpty()) {
                JSONObject first = scenarios.getJSONObject(0);
                if (first != null && first.getString("id") != null) {
                    result.put("activeScenarioId", first.getString("id"));
                }
            }
        }

        return ToolResultByteFit.fitFlowMeta(result, ctx.getMaxToolResultBytes());
    }

    private static JSONArray toScenarioSummaries(List<GraphRunScenario> scenarios) {
        JSONArray scenarioArr = new JSONArray();
        if (scenarios == null) {
            return scenarioArr;
        }
        for (GraphRunScenario scenario : scenarios) {
            if (scenario != null) {
                scenarioArr.add(FlowDesignScenarioJsonSupport.toSummary(scenario));
            }
        }
        return scenarioArr;
    }
}
