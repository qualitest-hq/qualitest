package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * update_flow_meta：浅合并更新测试流名称/说明并立即写库（不动画布）。
 * <p>
 * Web 造流助手与 MCP 自动写均可调用；成功即落盘，不进 Staging。
 */
@RequiredArgsConstructor
public class UpdateFlowMetaTool implements QualitestTool {

    private final ITestFlowService testFlowService;

    @Override
    public String getName() {
        return FlowDesignToolNames.UPDATE_FLOW_META.getId();
    }

    /**
     * 校验归属与入参 → 只改传入字段 → 写库并回执。
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx != null && ctx.isTemplateDesignMode()) {
            return FlowDesignToolSupport.errorJson("模板画布不支持修改测试流名称或说明");
        }
        Long projectId = ctx != null ? ctx.getTestProjectId() : null;
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        if (arguments == null) {
            return FlowDesignToolSupport.errorJson("至少传入 flowName 或 flowDescription");
        }

        Long flowId = FlowDesignToolSupport.longArg(arguments.get("testFlowId"));
        if (flowId == null && ctx != null) {
            flowId = ctx.getTestFlowId();
        }
        TestFlowAccessSupport.FlowAccess access = TestFlowAccessSupport.resolveFlowInProject(
                flowId, projectId, testFlowService);
        if (!access.isOk()) {
            return FlowDesignToolSupport.errorJson(access.errorMessage());
        }

        boolean hasName = arguments.containsKey("flowName");
        boolean hasDescription = arguments.containsKey("flowDescription");
        if (!hasName && !hasDescription) {
            return FlowDesignToolSupport.errorJson("至少传入 flowName 或 flowDescription");
        }

        String newName = null;
        if (hasName) {
            newName = FlowDesignToolSupport.stringArg(arguments.get("flowName"));
            if (newName.isEmpty()) {
                return FlowDesignToolSupport.errorJson("flowName 不能为空");
            }
        }
        String newDescription = null;
        if (hasDescription) {
            // 传空串表示清空说明；保留空串以便 Mapper 更新该列
            newDescription = FlowDesignToolSupport.stringArg(arguments.get("flowDescription"));
        }

        TestFlow toUpdate = TestFlow.builder()
                .testFlowId(flowId)
                .flowName(newName)
                .flowDescription(hasDescription ? newDescription : null)
                .build();

        try {
            FlowExternalChangeSourceHolder.set(
                    FlowExternalChangeSourceHolder.mcpOrWebAutopilot(
                            ctx != null && ctx.getAiChatSessionId() != null));
            testFlowService.updateTestFlow(toUpdate);
        } catch (ServiceException e) {
            return FlowDesignToolSupport.errorJson(e.getMessage());
        } catch (Exception e) {
            return FlowDesignToolSupport.errorJson("更新测试流元数据失败: " + e.getMessage());
        } finally {
            FlowExternalChangeSourceHolder.clear();
        }

        TestFlowResult existing = access.flow();
        String resultName = newName != null ? newName : existing.getFlowName();
        String resultDescription = hasDescription
                ? newDescription
                : (existing.getFlowDescription() != null ? existing.getFlowDescription() : "");

        JSONObject result = new JSONObject();
        result.put("testFlowId", String.valueOf(flowId));
        result.put("flowName", resultName != null ? resultName : "");
        result.put("flowDescription", resultDescription != null ? resultDescription : "");
        result.put("hint", "测试流名称/说明已更新并写库；未改动画布、场景与环境");
        return result.toJSONString();
    }
}
