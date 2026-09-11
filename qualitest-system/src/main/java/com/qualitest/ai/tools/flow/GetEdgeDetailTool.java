package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;

import java.util.Map;

/**
 * 只读：按 edgeId 返回单条边的完整字段（id/source/target/label）。
 * <p>
 * 读图走工作图优先：本轮已接受的 submit 单元已合并进工作图时可立刻查到新边。
 * 改边前应先调用本工具核对现有端点与标签。
 */
public class GetEdgeDetailTool implements QualitestTool {

    private final FlowGraphContextResolver graphResolver;

    public GetEdgeDetailTool(FlowGraphContextResolver graphResolver) {
        this.graphResolver = graphResolver;
    }

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_EDGE_DETAIL.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        String edgeId = FlowDesignToolSupport.stringArg(arguments.get("edgeId"));
        if (edgeId.isEmpty()) {
            return FlowDesignToolSupport.errorJson("缺少 edgeId");
        }
        FlowGraphContextResolver.ResolvedGraph resolved = graphResolver.resolve(arguments, ctx);
        if (!resolved.isOk()) {
            return resolved.errorJson();
        }
        GraphJson graph = resolved.graph();
        if (graph.getEdges() == null) {
            return FlowDesignToolSupport.missingGraphJsonError();
        }
        for (GraphEdge edge : graph.getEdges()) {
            if (edge != null && edgeId.equals(edge.getId())) {
                JSONObject result = new JSONObject();
                result.put("id", edge.getId());
                result.put("source", edge.getSource());
                result.put("target", edge.getTarget());
                if (edge.getLabel() != null) {
                    result.put("label", edge.getLabel());
                }
                return ToolResultByteFit.fitAck(result, ctx.getMaxToolResultBytes());
            }
        }
        return FlowDesignToolSupport.errorJson("边不存在: " + edgeId);
    }
}
