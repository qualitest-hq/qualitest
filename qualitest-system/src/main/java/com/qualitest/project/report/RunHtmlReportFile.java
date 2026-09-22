package com.qualitest.project.report;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 简易 HTML 运行报告产物：正文与建议下载文件名。
 */
@Getter
@AllArgsConstructor
public class RunHtmlReportFile {

    /** HTML 全文 */
    private final String html;

    /** 下载文件名（含 .html 后缀） */
    private final String fileName;
}
