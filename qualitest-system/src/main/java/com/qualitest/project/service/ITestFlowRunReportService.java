package com.qualitest.project.service;

import com.qualitest.project.report.RunHtmlReportFile;

/**
 * 测试流单次运行的标准报告服务。
 * 当前产出为单文件简易 HTML，供下载存档或发给他人查看。
 */
public interface ITestFlowRunReportService {

    /**
     * 生成指定运行的简易 HTML 报告。
     * 仅允许已结束的运行：成功、失败、中止、取消；执行中或暂停中不可生成。
     *
     * @param testFlowRunId 运行 ID
     * @return HTML 正文与建议下载文件名
     */
    RunHtmlReportFile buildHtmlReport(Long testFlowRunId);
}
