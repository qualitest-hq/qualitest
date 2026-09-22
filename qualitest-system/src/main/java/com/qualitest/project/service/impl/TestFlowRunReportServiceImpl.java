package com.qualitest.project.service.impl;

import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.report.RunHtmlReportFile;
import com.qualitest.project.report.RunHtmlReportRenderer;
import com.qualitest.project.result.TestFlowRunDetailResult;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowRunReportService;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 测试流运行标准报告业务实现。
 * 拉取运行详情与项目/流名称后，渲染为简易 HTML，并给出建议下载文件名。
 */
@Service
@RequiredArgsConstructor
public class TestFlowRunReportServiceImpl implements ITestFlowRunReportService {

    /** 运行详情（含步骤） */
    private final ITestFlowExecutionService testFlowExecutionService;

    /** 测试流名称查询 */
    private final ITestFlowService testFlowService;

    /** 测试项目名称查询 */
    private final ITestProjectService testProjectService;

    /**
     * 校验运行可导出后生成 HTML 报告与下载文件名。
     *
     * @param testFlowRunId 运行 ID
     * @return HTML 与文件名
     */
    @Override
    public RunHtmlReportFile buildHtmlReport(Long testFlowRunId) {
        if (testFlowRunId == null) {
            throw new ServiceException("testFlowRunId 不能为空");
        }
        TestFlowRunDetailResult detail = testFlowExecutionService.getRunDetail(testFlowRunId);
        TestFlowRunResult run = detail != null ? detail.getRun() : null;
        if (run == null) {
            throw new ServiceException("运行记录不存在");
        }
        // 执行中、暂停中不允许导出
        if (!RunStatus.isReportExportable(run.getStatus())) {
            throw new ServiceException("运行尚未结束，无法导出报告");
        }

        String flowName = null;
        String projectName = null;
        if (run.getTestFlowId() != null) {
            TestFlow flow = testFlowService.selectTestFlowById(run.getTestFlowId());
            if (flow != null) {
                flowName = flow.getFlowName();
                if (flow.getTestProjectId() != null) {
                    TestProject project = testProjectService.selectTestProjectById(flow.getTestProjectId());
                    if (project != null) {
                        projectName = project.getProjectName();
                    }
                }
            }
        }
        return RunHtmlReportRenderer.toReportFile(detail, projectName, flowName);
    }
}
