package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.context.EntryScenarioSupport;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.List;
import java.util.Map;

/**
 * 读取当前测试流画布元数据与场景摘要。
 * <p>
 * meta 回执含 startNodeId 等；scenarios / activeScenarioId 取自 scenario-entry.data.scenarios。
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
     * 组装 meta 与场景摘要 JSON；解析失败时返回错误回执。
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
        result.put("startNodeId", "");
        result.put("entryNodeId", "");

        GraphMeta meta = graph.getMeta();
        if (meta != null && meta.getStartNodeId() != null) {
            result.put("startNodeId", meta.getStartNodeId());
        }

        GraphNode entry = EntryScenarioSupport.findScenarioEntry(graph, null);
        List<GraphRunScenario> scenarios = EntryScenarioSupport.scenariosOf(entry);
        if (entry != null) {
            result.put("entryNodeId", entry.getId());
        }
        if (!scenarios.isEmpty() && scenarios.get(0).getId() != null) {
            result.put("activeScenarioId", scenarios.get(0).getId());
        }

        JSONArray scenarioArr = new JSONArray();
        for (GraphRunScenario scenario : scenarios) {
            if (scenario != null) {
                scenarioArr.add(FlowDesignScenarioJsonSupport.toSummary(scenario));
            }
        }
        result.put("scenarios", scenarioArr);
        return ToolResultByteFit.fitFlowMeta(result, ctx.getMaxToolResultBytes());
    }
}