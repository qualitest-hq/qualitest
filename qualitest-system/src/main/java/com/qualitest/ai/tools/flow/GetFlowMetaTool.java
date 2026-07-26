package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.subflow.GraphMetaIoSupport;

import java.util.Map;

/** get_flow_meta：返回 graphJson.meta 运行场景、flowOutputs 与 startNodeId 摘要；MCP 可仅传 testFlowId 自动加载。 */
public class GetFlowMetaTool implements QualitestTool {

    private final FlowGraphContextResolver graphResolver;

    public GetFlowMetaTool(FlowGraphContextResolver graphResolver) {
        this.graphResolver = graphResolver;
    }

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_FLOW_META.getId();
    }

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
        result.put("flowOutputNames", new JSONArray());
        result.put("startNodeId", "");
        if (graph.getMeta() == null) {
            return FlowDesignToolSupport.enforceByteLimit(result, ctx.getMaxToolResultBytes());
        }
        GraphMeta meta = graph.getMeta();
        if (meta.getStartNodeId() != null) {
            result.put("startNodeId", meta.getStartNodeId());
        }
        if (meta.getActiveScenarioId() != null) {
            result.put("activeScenarioId", meta.getActiveScenarioId());
        }
        JSONArray scenarios = new JSONArray();
        if (meta.getScenarios() != null) {
            for (GraphRunScenario scenario : meta.getScenarios()) {
                if (scenario == null) {
                    continue;
                }
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
                scenarios.add(item);
            }
        }
        result.put("scenarios", scenarios);
        result.put("flowOutputNames", GraphMetaIoSupport.flowOutputNames(meta));
        return FlowDesignToolSupport.enforceByteLimit(result, ctx.getMaxToolResultBytes());
    }
}
