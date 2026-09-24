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

import java.util.Map;

/**
 * 列流工具：按当前项目返回测试流摘要（不含画布 JSON）。
 * 可选按名称关键字、目录、仅未分组过滤；每条带目录 id 与名称。
 */
@RequiredArgsConstructor
public class ListFlowsTool implements QualitestTool {

    /** 未传 limit 时的默认返回条数 */
    public static final int DEFAULT_LIMIT = 20;

    private final ITestFlowService testFlowService;

    @Override
    public String getName() {
        return FlowDesignToolNames.LIST_FLOWS.getId();
    }

    /**
     * 解析过滤参数 → 列流 → 组装 JSON 条目。
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        String keyword = FlowDesignToolSupport.stringArg(arguments.get("keyword"));
        int limit = FlowDesignToolSupport.resolveListLimit(
                arguments.get("limit"), DEFAULT_LIMIT, ctx.getMaxListFlows());
        Long flowGroupId = FlowDesignToolSupport.longArg(arguments.get("flowGroupId"));
        Boolean ungroupedOnly = null;
        Object ungroupedRaw = arguments.get("ungroupedOnly");
        if (ungroupedRaw instanceof Boolean b) {
            ungroupedOnly = b;
        } else if (ungroupedRaw != null) {
            String s = String.valueOf(ungroupedRaw).trim();
            if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
                ungroupedOnly = true;
            } else if ("false".equalsIgnoreCase(s) || "0".equals(s)) {
                ungroupedOnly = false;
            }
        }
        TestFlowLister.ListFlowsResult listed = TestFlowLister.listFlows(
                testFlowService, ctx.getTestProjectId(), keyword, limit, flowGroupId, ungroupedOnly);
        JSONArray items = new JSONArray();
        for (TestFlowResult flow : listed.flows()) {
            if (flow == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("testFlowId", String.valueOf(flow.getTestFlowId()));
            item.put("flowName", flow.getFlowName());
            item.put("flowDescription", flow.getFlowDescription() != null ? flow.getFlowDescription() : "");
            if (flow.getFlowGroupId() != null) {
                item.put("flowGroupId", String.valueOf(flow.getFlowGroupId()));
            }
            item.put("flowGroupName", flow.getFlowGroupName() != null ? flow.getFlowGroupName() : "");
            items.add(item);
        }
        String hint = listed.truncated()
                ? "匹配结果超过 limit=" + limit + "，请缩小 keyword 或调用 get_subflow_detail / get_flow 查看单条"
                : "选定 testFlowId 后可直接调用 get_graph_summary / get_flow_meta（无需 graphJson 信封）";
        return FlowDesignToolSupport.buildItemsResult(items, listed.truncated(), hint, ctx.getMaxToolResultBytes());
    }
}
