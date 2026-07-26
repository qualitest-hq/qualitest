package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.subflow.GraphMetaIoSupport;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * {@code get_subflow_detail} 工具实现。
 * <p>
 * 按测试流 ID 加载其当前 {@code graph_json}，返回拓扑摘要与对外输出声明，
 * 无需 graphJson 信封。
 */
@RequiredArgsConstructor
public class GetSubflowDetailTool implements QualitestTool {

    private final ITestFlowService testFlowService;

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_SUBFLOW_DETAIL.getId();
    }

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
        result.put("flowOutputNames", meta != null ? GraphMetaIoSupport.flowOutputNames(meta) : new JSONArray());

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
        return FlowDesignToolSupport.enforceByteLimit(result, ctx.getMaxToolResultBytes());
    }
}
