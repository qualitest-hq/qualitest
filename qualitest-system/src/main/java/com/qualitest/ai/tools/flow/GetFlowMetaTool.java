package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;
import com.qualitest.flow.subflow.GraphMetaIoSupport;

import java.util.Map;

/**
 * 读取当前测试流画布元数据。
 * <p>
 * 回执含开始节点、激活场景、场景摘要、对外产出声明（含说明）及产出名称列表；
 * 返回前按体积上限裁剪场景与产出字段。
 */
public class GetFlowMetaTool implements QualitestTool {

    private final FlowGraphContextResolver graphResolver;

    /**
     * @param graphResolver 解析当前画布图
     */
    public GetFlowMetaTool(FlowGraphContextResolver graphResolver) {
        this.graphResolver = graphResolver;
    }

    /**
     * 返回工具名称。
     *
     * @return 工具 id
     */
    @Override
    public String getName() {
        return FlowDesignToolNames.GET_FLOW_META.getId();
    }

    /**
     * 组装画布元数据回执。
     *
     * @param arguments 工具参数
     * @param ctx       请求上下文
     * @return JSON 回执或错误
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
        result.put("flowOutputs", new JSONArray());
        result.put("flowOutputNames", new JSONArray());
        result.put("startNodeId", "");
        if (graph.getMeta() == null) {
            return ToolResultByteFit.fitFlowMeta(result, ctx.getMaxToolResultBytes());
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
                if (scenario != null) {
                    scenarios.add(FlowDesignScenarioJsonSupport.toSummary(scenario));
                }
            }
        }
        result.put("scenarios", scenarios);
        // 完整产出声明与名称列表一并返回，便于编排与兼容旧字段
        result.put("flowOutputs", GraphMetaIoSupport.flowOutputsJson(meta));
        result.put("flowOutputNames", GraphMetaIoSupport.flowOutputNames(meta));
        return ToolResultByteFit.fitFlowMeta(result, ctx.getMaxToolResultBytes());
    }
}
