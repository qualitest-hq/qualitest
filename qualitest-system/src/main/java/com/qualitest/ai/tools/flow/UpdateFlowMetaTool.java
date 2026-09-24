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
 * 浅合并更新测试流元数据并立即写库（不改画布节点与边）。
 * 可改名称、说明、所属目录；目录传空串表示改为未分组。
 */
@RequiredArgsConstructor
public class UpdateFlowMetaTool implements QualitestTool {

    private final ITestFlowService testFlowService;

    @Override
    public String getName() {
        return FlowDesignToolNames.UPDATE_FLOW_META.getId();
    }

    /**
     * 解析要改的字段 → 写库 → 回执当前名称/说明/目录。
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
            return FlowDesignToolSupport.errorJson("至少传入 flowName、flowDescription 或 flowGroupId");
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
        boolean hasGroup = arguments.containsKey("flowGroupId");
        if (!hasName && !hasDescription && !hasGroup) {
            return FlowDesignToolSupport.errorJson("至少传入 flowName、flowDescription 或 flowGroupId");
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

        Long newGroupId = null;
        boolean clearGroup = false;
        if (hasGroup) {
            Object rawGroup = arguments.get("flowGroupId");
            if (rawGroup == null || "".equals(String.valueOf(rawGroup).trim())) {
                clearGroup = true;
            } else {
                newGroupId = FlowDesignToolSupport.longArg(rawGroup);
                if (newGroupId == null) {
                    return FlowDesignToolSupport.errorJson("flowGroupId 无效");
                }
            }
        }

        TestFlow toUpdate = TestFlow.builder()
                .testFlowId(flowId)
                .flowName(newName)
                .flowDescription(hasDescription ? newDescription : null)
                .flowGroupId(newGroupId)
                .clearFlowGroup(clearGroup ? Boolean.TRUE : null)
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
        if (clearGroup) {
            result.put("flowGroupId", "");
            result.put("flowGroupName", "");
        } else if (newGroupId != null) {
            result.put("flowGroupId", String.valueOf(newGroupId));
        } else if (existing.getFlowGroupId() != null) {
            result.put("flowGroupId", String.valueOf(existing.getFlowGroupId()));
            result.put("flowGroupName", existing.getFlowGroupName() != null ? existing.getFlowGroupName() : "");
        }
        result.put("hint", "测试流元数据已更新并写库；未改动画布、场景与环境");
        return result.toJSONString();
    }
}
