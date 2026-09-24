package com.qualitest.ai.tools.flow;

import com.qualitest.project.params.TestFlowParams;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 按项目列举测试流的公共逻辑。
 * 供列流、列子流模板等工具复用：查库、按关键字/目录过滤、截断条数。
 */
public final class TestFlowLister {

    private TestFlowLister() {
    }

    /**
     * 列举结果：本页流列表，以及是否因超限被截断。
     */
    public record ListFlowsResult(List<TestFlowResult> flows, boolean truncated) {
    }

    /**
     * 按项目列举测试流（可按名称关键字过滤）。
     *
     * @param testFlowService 测试流业务
     * @param testProjectId   项目主键
     * @param keyword         名称关键字，可空
     * @param limit           返回条数上限
     * @return 列表与是否截断
     */
    public static ListFlowsResult listFlows(ITestFlowService testFlowService,
                                            Long testProjectId,
                                            String keyword,
                                            int limit) {
        return listFlows(testFlowService, testProjectId, keyword, limit, null, null);
    }

    /**
     * 按项目列举测试流，支持名称关键字、目录、仅未分组。
     *
     * @param testFlowService 测试流业务
     * @param testProjectId   项目主键
     * @param keyword         名称关键字，可空
     * @param limit           返回条数上限
     * @param flowGroupId     目录主键（含子孙），可空
     * @param ungroupedOnly   true 时只返回未挂目录的流
     * @return 列表与是否截断
     */
    public static ListFlowsResult listFlows(ITestFlowService testFlowService,
                                            Long testProjectId,
                                            String keyword,
                                            int limit,
                                            Long flowGroupId,
                                            Boolean ungroupedOnly) {
        TestFlowParams params = TestFlowParams.builder()
                .testProjectId(testProjectId)
                .build();
        if (keyword != null && !keyword.isBlank()) {
            params.setFlowName(keyword);
        }
        if (Boolean.TRUE.equals(ungroupedOnly)) {
            params.setUngroupedOnly(true);
        } else if (flowGroupId != null) {
            params.setFlowGroupId(flowGroupId);
        }
        List<TestFlowResult> flows = testFlowService.selectTestFlowResultList(params);
        boolean truncated = flows.size() > limit;
        if (truncated) {
            flows = flows.stream().limit(limit).collect(Collectors.toList());
        }
        return new ListFlowsResult(flows, truncated);
    }
}
