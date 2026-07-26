package com.qualitest.ai.tools.flow;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;

/**
 * 按 testFlowId 加载测试流并校验项目归属（get_flow、get_subflow_detail、FlowGraphContextResolver 共用）。
 */
public final class TestFlowAccessSupport {

    private TestFlowAccessSupport() {
    }

    public record FlowAccess(TestFlowResult flow, String errorMessage) {
        public boolean isOk() {
            return errorMessage == null && flow != null;
        }
    }

    public static FlowAccess resolveFlowInProject(Long flowId,
                                                  Long testProjectId,
                                                  ITestFlowService testFlowService) {
        if (flowId == null) {
            return new FlowAccess(null, "缺少 testFlowId");
        }
        TestFlowResult flow = testFlowService.selectTestFlowResult(flowId);
        if (flow == null) {
            return new FlowAccess(null, "测试流不存在");
        }
        if (flow.getTestProjectId() == null || !flow.getTestProjectId().equals(testProjectId)) {
            return new FlowAccess(null, "测试流不属于当前项目");
        }
        return new FlowAccess(flow, null);
    }

    public static GraphJson parseGraphJson(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            return GraphJson.builder().build();
        }
        try {
            GraphJson parsed = GraphJson.parse(graphJson);
            return parsed != null ? parsed : GraphJson.builder().build();
        } catch (Exception e) {
            return GraphJson.builder().build();
        }
    }

    /** 解析 graph_json 文本；失败时返回 true 供调用方写入 graphParseFailed 警告。 */
    public static boolean isGraphParseFailed(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            return false;
        }
        try {
            GraphJson parsed = GraphJson.parse(graphJson);
            return parsed == null;
        } catch (Exception e) {
            return true;
        }
    }
}
