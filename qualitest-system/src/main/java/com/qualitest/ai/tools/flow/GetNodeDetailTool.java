package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;

import java.util.Map;

/**
 * 按 nodeId 返回节点 type 与完整 data。
 * <p>
 * 返回前按节点详情形状做字节上限裁剪（压缩脚本/body 等大字段）。
 */
public class GetNodeDetailTool implements QualitestTool {

    private final FlowGraphContextResolver graphResolver;

    public GetNodeDetailTool(FlowGraphContextResolver graphResolver) {
        this.graphResolver = graphResolver;
    }

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_NODE_DETAIL.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        String nodeId = FlowDesignToolSupport.stringArg(arguments.get("nodeId"));
        if (nodeId.isEmpty()) {
            return FlowDesignToolSupport.errorJson("缺少 nodeId");
        }
        FlowGraphContextResolver.ResolvedGraph resolved = graphResolver.resolve(arguments, ctx);
        if (!resolved.isOk()) {
            return resolved.errorJson();
        }
        GraphJson graph = resolved.graph();
        if (graph.getNodes() == null) {
            return FlowDesignToolSupport.missingGraphJsonError();
        }
        for (GraphNode node : graph.getNodes()) {
            if (node != null && nodeId.equals(node.getId())) {
                JSONObject result = new JSONObject();
                result.put("id", node.getId());
                result.put("type", node.getType());
                result.put("data", node.getData());
                if (node.getPosition() != null) {
                    result.put("position", node.getPosition());
                }
                return ToolResultByteFit.fitNodeDetail(result, ctx.getMaxToolResultBytes());
            }
        }
        return FlowDesignToolSupport.errorJson("节点不存在: " + nodeId);
    }
}
