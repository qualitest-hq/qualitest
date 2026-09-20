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
 * 只读：按 nodeId 返回节点 id、type 与完整 data。
 * <p>
 * 读图走工作图优先（本轮已接受的 submit 单元可立刻查到）。
 * 回执字段刻意不含 position（模型无需关心画布落点）；
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
                return ToolResultByteFit.fitNodeDetail(result, ctx.getMaxToolResultBytes());
            }
        }
        return FlowDesignToolSupport.errorJson("节点不存在: " + nodeId);
    }
}
