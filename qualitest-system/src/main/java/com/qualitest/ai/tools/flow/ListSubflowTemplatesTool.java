package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.ai.tools.ToolResultByteFit;
import com.qualitest.flow.subflow.SubflowTemplateCatalog;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 汇总平台内置子流模板与项目内可引用测试流摘要，供 subflow 节点选型。
 * <p>
 * 返回前按模板列表形状做字节上限裁剪（先裁项目流，再裁平台模板）。
 */
@RequiredArgsConstructor
public class ListSubflowTemplatesTool implements QualitestTool {

    private final ITestFlowService testFlowService;

    @Override
    public String getName() {
        return FlowDesignToolNames.LIST_SUBFLOW_TEMPLATES.getId();
    }

    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        JSONArray platform = new JSONArray();
        for (JSONObject tpl : SubflowTemplateCatalog.listTemplates()) {
            platform.add(tpl);
        }

        String keyword = FlowDesignToolSupport.stringArg(arguments.get("keyword"));
        int limit = FlowDesignToolSupport.resolveListLimit(
                arguments.get("limit"), ListFlowsTool.DEFAULT_LIMIT, ctx.getMaxListFlows());
        TestFlowLister.ListFlowsResult listed = TestFlowLister.listFlows(
                testFlowService, ctx.getTestProjectId(), keyword, limit);

        JSONArray project = new JSONArray();
        for (TestFlowResult flow : listed.flows()) {
            if (flow == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("testFlowId", String.valueOf(flow.getTestFlowId()));
            item.put("flowName", flow.getFlowName());
            item.put("flowDescription", flow.getFlowDescription() != null ? flow.getFlowDescription() : "");
            project.add(item);
        }

        JSONObject result = new JSONObject();
        result.put("platformTemplates", platform);
        result.put("projectSubflows", project);
        result.put("truncated", listed.truncated());
        String hint = "主流程添加 subflow 节点时：可引用 projectSubflows 中任意 testFlowId，"
                + "或从 platformTemplates 复制为新测试流后引用";
        if (listed.truncated()) {
            hint += "；projectSubflows 超过 limit=" + limit + "，请传入 keyword 缩小范围";
        }
        result.put("hint", hint);
        return ToolResultByteFit.fitSubflowTemplates(result, ctx.getMaxToolResultBytes());
    }
}
