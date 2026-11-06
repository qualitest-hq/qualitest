package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.model.GraphFlowOutput;
import com.qualitest.flow.subflow.GraphMetaIoSupport;
import com.qualitest.flow.sync.FlowEditLeaseConflictException;
import com.qualitest.flow.sync.FlowExternalChangeSourceHolder;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 更新测试流元数据并立即写库。
 * <p>
 * 可改名称、说明、所属目录；也可整表替换图元数据中的对外产出声明，
 * 不修改节点、边与运行场景。
 */
@RequiredArgsConstructor
public class UpdateFlowMetaTool implements QualitestTool {

    private final ITestFlowService testFlowService;

    /**
     * 返回工具名称。
     *
     * @return 工具 id
     */
    @Override
    public String getName() {
        return FlowDesignToolNames.UPDATE_FLOW_META.getId();
    }

    /**
     * 解析入参并写库，回执当前名称、说明、目录与（若本次有改）对外产出声明。
     *
     * @param arguments 工具参数
     * @param ctx       请求上下文
     * @return JSON 回执或错误
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
            return FlowDesignToolSupport.errorJson(
                    "至少传入 flowName、flowDescription、flowGroupId 或 flowOutputs");
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
        boolean hasFlowOutputs = arguments.containsKey("flowOutputs");
        if (!hasName && !hasDescription && !hasGroup && !hasFlowOutputs) {
            return FlowDesignToolSupport.errorJson(
                    "至少传入 flowName、flowDescription、flowGroupId 或 flowOutputs");
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
            // 传空串表示清空说明，需保留空串以便更新该列
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

        List<GraphFlowOutput> flowOutputs = null;
        if (hasFlowOutputs) {
            try {
                flowOutputs = GraphMetaIoSupport.parseFlowOutputsArg(arguments.get("flowOutputs"));
            } catch (IllegalArgumentException e) {
                return FlowDesignToolSupport.errorJson(e.getMessage());
            }
        }

        try {
            FlowExternalChangeSourceHolder.set(
                    FlowExternalChangeSourceHolder.mcpOrWebAutopilot(
                            ctx != null && ctx.getAiChatSessionId() != null));

            // 名称 / 说明 / 目录：走元数据更新，不写图画布
            if (hasName || hasDescription || hasGroup) {
                TestFlow toUpdate = TestFlow.builder()
                        .testFlowId(flowId)
                        .flowName(newName)
                        .flowDescription(hasDescription ? newDescription : null)
                        .flowGroupId(newGroupId)
                        .clearFlowGroup(clearGroup ? Boolean.TRUE : null)
                        .build();
                testFlowService.updateTestFlow(toUpdate);
            }

            // 对外产出声明：整表替换图元数据字段，并刷新本轮工作图版本
            if (hasFlowOutputs) {
                String endpoint = FlowDesignAutopilotCommitSupport.resolveEndpointId(ctx);
                ITestFlowService.ApplyGraphPatchResult applied = testFlowService.replaceFlowOutputs(
                        flowId, projectId, flowOutputs, endpoint);
                if (ctx != null) {
                    ctx.advanceWorkingGraph(applied.graph());
                    ctx.setBaseGraphRevision(applied.revision());
                    ctx.notifyGraphCommitted(applied.graph());
                }
            }
        } catch (FlowEditLeaseConflictException e) {
            return FlowDesignToolSupport.errorJson(
                    e.getMessage() != null ? e.getMessage() : "写锁冲突，请稍后重试");
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
        if (hasFlowOutputs) {
            result.put("flowOutputs", GraphMetaIoSupport.flowOutputsJson(flowOutputs));
        }
        result.put("hint", hasFlowOutputs
                ? "测试流元数据已更新并写库；已替换 meta.flowOutputs，未改节点/边/场景"
                : "测试流元数据已更新并写库；未改节点/边/场景");
        return result.toJSONString();
    }
}
