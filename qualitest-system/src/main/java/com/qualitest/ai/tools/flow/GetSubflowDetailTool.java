package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.subflow.SubflowInputSuggestSupport;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 按测试流 id 加载图画布，返回拓扑摘要与输入建议。
 * <p>
 * 回执含节点/边摘要、按主入口首条场景 inSeed 生成的 suggestedInputs；
 * 子流定稿出参挂在父图 nodes.&lt;subflowNodeId&gt;.out；返回前按体积上限裁剪。
 */
@RequiredArgsConstructor
public class GetSubflowDetailTool implements QualitestTool {

    private final ITestFlowService testFlowService;

    /**
     * 返回工具名称。
     *
     * @return 工具 id
     */
    @Override
    public String getName() {
        return FlowDesignToolNames.GET_SUBFLOW_DETAIL.getId();
    }

    /**
     * 组装被引用测试流的拓扑摘要与 suggestedInputs 回执。
     *
     * @param arguments 工具参数
     * @param ctx       请求上下文
     * @return JSON 回执或错误
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long flowId = FlowDesignToolSupport.longArg(arguments.get("testFlowId"));
        if (flowId == null) {
            flowId = ctx.getTestFlowId();
        }
        TestFlowAccessSupport.FlowAccess access = TestFlowAccessSupport.resolveFlowInProject(
                flowId, ctx.getTestProjectId(), testFlowService);
        if (!access.isOk()) {
            return FlowDesignToolSupport.errorJson(access.errorMessage());
        }
        var flow = access.flow();
        boolean graphParseFailed = TestFlowAccessSupport.isGraphParseFailed(flow.getGraphJson());
        GraphJson graph = TestFlowAccessSupport.parseGraphJson(flow.getGraphJson());

        JSONObject result = new JSONObject();
        result.put("testFlowId", String.valueOf(flow.getTestFlowId()));
        result.put("flowName", flow.getFlowName() != null ? flow.getFlowName() : "");
        result.put("flowDescription", flow.getFlowDescription() != null ? flow.getFlowDescription() : "");
        if (graphParseFailed) {
            result.put("graphParseFailed", true);
            result.put("warning", "graph_json 解析失败，拓扑摘要可能不完整");
        }

        GraphMeta meta = graph.getMeta();
        result.put("startNodeId", meta != null && meta.getStartNodeId() != null ? meta.getStartNodeId() : "");
        result.put("suggestedInputs", SubflowInputSuggestSupport.suggestInputMappingsJson(graph));

        int nodeCount = graph.getNodes() != null ? graph.getNodes().size() : 0;
        int edgeCount = graph.getEdges() != null ? graph.getEdges().size() : 0;
        result.put("nodeCount", nodeCount);
        result.put("edgeCount", edgeCount);
        result.put("nodeTypeCounts", FlowGraphSummarySupport.buildNodeTypeCounts(graph));

        JSONArray nodes = new JSONArray();
        if (graph.getNodes() != null) {
            for (GraphNode node : graph.getNodes()) {
                if (node == null) {
                    continue;
                }
                nodes.add(FlowGraphSummarySupport.toNodeSummaryItem(node));
            }
        }
        result.put("nodes", nodes);
        result.put("edges", FlowGraphSummarySupport.buildEdgeArray(graph.getEdges()));
        result.put("hint", "无需 graphJson 信封；查看单节点完整 data 请用 get_flow 或带 testFlowId 的 get_node_detail");
        return ToolResultByteFit.fitGraphTopology(result, ctx.getMaxToolResultBytes());
    }
}
