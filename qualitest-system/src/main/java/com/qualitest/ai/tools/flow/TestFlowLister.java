package com.qualitest.ai.tools.flow;

import com.qualitest.project.params.TestFlowParams;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 按项目列举测试流的公共逻辑（list_flows / list_subflow_templates 共用）。
 */
public final class TestFlowLister {

    private TestFlowLister() {
    }

    public record ListFlowsResult(List<TestFlowResult> flows, boolean truncated) {
    }

    public static ListFlowsResult listFlows(ITestFlowService testFlowService,
                                            Long testProjectId,
                                            String keyword,
                                            int limit) {
        TestFlowParams params = TestFlowParams.builder()
                .testProjectId(testProjectId)
                .build();
        if (keyword != null && !keyword.isBlank()) {
            params.setFlowName(keyword);
        }
        List<TestFlowResult> flows = testFlowService.selectTestFlowResultList(params);
        boolean truncated = flows.size() > limit;
        if (truncated) {
            flows = flows.stream().limit(limit).collect(Collectors.toList());
        }
        return new ListFlowsResult(flows, truncated);
    }
}
