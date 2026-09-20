package com.qualitest.ai.tools.flow;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * create_flow：在当前项目新建一条空画布测试流并立即写库（仅 MCP 自动写）。
 */
@RequiredArgsConstructor
public class CreateFlowTool implements QualitestTool {

    private final ITestFlowService testFlowService;

    @Override
    public String getName() {
        return FlowDesignToolNames.CREATE_FLOW.getId();
    }

    /**
     * 校验名称 → 插入空图测试流 → 回执 testFlowId。
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        if (ctx != null && ctx.isTemplateDesignMode()) {
            return FlowDesignToolSupport.errorJson("模板画布不支持新建测试流");
        }
        Long projectId = ctx != null ? ctx.getTestProjectId() : null;
        if (projectId == null) {
            return FlowDesignToolSupport.errorJson("缺少 testProjectId");
        }
        String flowName = FlowDesignToolSupport.stringArg(arguments.get("flowName")).trim();
        if (flowName.isEmpty()) {
            return FlowDesignToolSupport.errorJson("缺少 flowName");
        }
        String flowDescription = FlowDesignToolSupport.stringArg(arguments.get("flowDescription")).trim();
        if (flowDescription.isEmpty()) {
            flowDescription = null;
        }

        long testFlowId = IdUtil.getSnowflakeNextId();
        TestFlow flow = TestFlow.builder()
                .testFlowId(testFlowId)
                .testProjectId(projectId)
                .flowName(flowName)
                .flowDescription(flowDescription)
                .graphJson(EmptyTestFlowGraphFactory.createEmptyGraphJson())
                .build();
        try {
            testFlowService.insertTestFlow(flow);
        } catch (ServiceException e) {
            return FlowDesignToolSupport.errorJson(e.getMessage());
        } catch (Exception e) {
            return FlowDesignToolSupport.errorJson("新建测试流失败: " + e.getMessage());
        }

        JSONObject result = new JSONObject();
        result.put("testFlowId", String.valueOf(testFlowId));
        result.put("flowName", flowName);
        result.put("flowDescription", flowDescription != null ? flowDescription : "");
        result.put("hint", "空测试流已创建并写库；后续 submit_* / run_test_flow 请带此 testFlowId");
        return result.toJSONString();
    }
}
