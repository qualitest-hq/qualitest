package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.context.EntryScenarioSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.List;
import java.util.Map;

/**
 * 只读：按 scenarioId 返回单个运行场景配置。
 * <p>
 * 仅从 scenario-entry 查找；inSeed 只回键名。
 */
public class GetScenarioDetailTool implements QualitestTool {

    private final FlowGraphContextResolver graphResolver;

    public GetScenarioDetailTool(FlowGraphContextResolver graphResolver) {
        this.graphResolver = graphResolver;
    }

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_SCENARIO_DETAIL.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        String scenarioId = FlowDesignToolSupport.stringArg(arguments.get("scenarioId"));
        if (scenarioId.isEmpty()) {
            return FlowDesignToolSupport.errorJson("缺少 scenarioId");
        }
        String entryNodeId = FlowDesignToolSupport.stringArg(arguments.get("entryNodeId"));
        if (entryNodeId.isEmpty()) {
            entryNodeId = null;
        }
        FlowGraphContextResolver.ResolvedGraph resolved = graphResolver.resolve(arguments, ctx);
        if (!resolved.isOk()) {
            return resolved.errorJson();
        }
        GraphJson graph = resolved.graph();
        GraphNode entry = EntryScenarioSupport.findScenarioEntry(graph, entryNodeId);
        List<GraphRunScenario> scenarios = EntryScenarioSupport.scenariosOf(entry);
        if (scenarios.isEmpty()) {
            return FlowDesignToolSupport.errorJson("缺少 scenario-entry 场景配置");
        }
        for (GraphRunScenario scenario : scenarios) {
            if (scenario != null && scenarioId.equals(scenario.getId())) {
                JSONObject result = FlowDesignScenarioJsonSupport.toSummary(scenario);
                String activeId = scenarios.get(0).getId();
                result.put("active", scenarioId.equals(activeId));
                if (entry != null) {
                    result.put("entryNodeId", entry.getId());
                }
                return ToolResultByteFit.fitAck(result, ctx.getMaxToolResultBytes());
            }
        }
        return FlowDesignToolSupport.errorJson("场景不存在: " + scenarioId);
    }
}