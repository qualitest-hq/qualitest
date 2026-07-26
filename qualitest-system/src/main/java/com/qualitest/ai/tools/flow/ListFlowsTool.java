package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * list_flows：按项目列举测试流摘要（testFlowId、flowName 等，不含 graphJson）。
 */
@RequiredArgsConstructor
public class ListFlowsTool implements QualitestTool {

    /** 未传 limit 时的默认条数 */
    public static final int DEFAULT_LIMIT = 20;

    private final ITestFlowService testFlowService;

    @Override
    public String getName() {
        return FlowDesignToolNames.LIST_FLOWS.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        String keyword = FlowDesignToolSupport.stringArg(arguments.get("keyword"));
        int limit = FlowDesignToolSupport.resolveListLimit(
                arguments.get("limit"), DEFAULT_LIMIT, ctx.getMaxListFlows());
        TestFlowLister.ListFlowsResult listed = TestFlowLister.listFlows(
                testFlowService, ctx.getTestProjectId(), keyword, limit);
        JSONArray items = new JSONArray();
        for (TestFlowResult flow : listed.flows()) {
            if (flow == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("testFlowId", String.valueOf(flow.getTestFlowId()));
            item.put("flowName", flow.getFlowName());
            item.put("flowDescription", flow.getFlowDescription() != null ? flow.getFlowDescription() : "");
            items.add(item);
        }
        String hint = listed.truncated()
                ? "匹配结果超过 limit=" + limit + "，请缩小 keyword 或调用 get_subflow_detail / get_flow 查看单条"
                : "选定 testFlowId 后可直接调用 get_graph_summary / get_flow_meta（无需 graphJson 信封）";
        return FlowDesignToolSupport.buildItemsResult(items, listed.truncated(), hint, ctx.getMaxToolResultBytes());
    }
}
