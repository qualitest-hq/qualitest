package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.Map;

/**
 * 只读：按 scenarioId 返回单个运行场景配置。
 * <p>
 * flowSeed 只回键名不回明文；额外带 active 表示是否为当前默认场景。
 * 读图走工作图优先，便于改场景前核对本轮已提交的场景单元。
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
        FlowGraphContextResolver.ResolvedGraph resolved = graphResolver.resolve(arguments, ctx);
        if (!resolved.isOk()) {
            return resolved.errorJson();
        }
        GraphJson graph = resolved.graph();
        GraphMeta meta = graph.getMeta();
        if (meta == null || meta.getScenarios() == null) {
            return FlowDesignToolSupport.errorJson("场景不存在: " + scenarioId);
        }
        for (GraphRunScenario scenario : meta.getScenarios()) {
            if (scenario != null && scenarioId.equals(scenario.getId())) {
                JSONObject result = FlowDesignScenarioJsonSupport.toSummary(scenario);
                result.put("active", scenarioId.equals(meta.getActiveScenarioId()));
                return ToolResultByteFit.fitAck(result, ctx.getMaxToolResultBytes());
            }
        }
        return FlowDesignToolSupport.errorJson("场景不存在: " + scenarioId);
    }
}
