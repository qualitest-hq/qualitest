package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 读取单条测试流元数据与 graphJson。
 * <p>
 * 返回前按流记录形状做字节上限裁剪（优先去掉 graphJson）。
 */
@RequiredArgsConstructor
public class GetFlowTool implements QualitestTool {

    private final ITestFlowService testFlowService;

    @Override
    public String getName() {
        return FlowDesignToolNames.GET_FLOW.getId();
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
        JSONObject result = new JSONObject();
        result.put("testFlowId", String.valueOf(flow.getTestFlowId()));
        result.put("flowName", flow.getFlowName());
        result.put("flowDescription", flow.getFlowDescription() != null ? flow.getFlowDescription() : "");
        result.put("graphJson", FlowDesignToolSupport.parseGraphJsonField(flow.getGraphJson()));
        result.put("hint", "浏览拓扑优先 get_subflow_detail 或带 testFlowId 的 get_graph_summary；"
                + "需完整 data 编辑时可将 graphJson 放入信封后调用 get_node_detail");
        return ToolResultByteFit.fitFlowRecord(result, ctx.getMaxToolResultBytes());
    }
}
