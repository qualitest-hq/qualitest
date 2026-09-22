package com.qualitest.web.controller.project;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.utils.file.FileUtils;
import com.qualitest.project.params.TestFlowRunParams;
import com.qualitest.project.params.ResumeTestFlowRunParams;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.result.ResumeRunResult;
import com.qualitest.project.result.TestFlowRunDetailResult;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowRunReportService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.common.core.page.TableDataInfo;

/**
 * 测试流运行 HTTP 接口。
 * <p>
 * trigger：校验通过后写 running 记录并立刻返回 runId，图在后台执行；
 * getInfo：查 Run 头、步骤与图快照，执行中也可轮询。
 */
@RestController
@RequestMapping("/project/testFlowRun")
@AllArgsConstructor
public class TestFlowRunController extends BaseController {

    private final ITestFlowRunService testFlowRunService;
    private final ITestFlowExecutionService testFlowExecutionService;
    private final ITestFlowRunReportService testFlowRunReportService;

    /**
     * 查询测试流运行列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestFlowRunParams params) {
        startPage();
        List<TestFlowRunResult> list = testFlowRunService.selectTestFlowRunResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出测试流运行列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:export')")
    @Log(title = "测试流运行", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TestFlowRunParams params) {
        List<TestFlowRunResult> list = testFlowRunService.selectTestFlowRunResultList(params);
        ExcelUtil<TestFlowRunResult> util = new ExcelUtil<>(TestFlowRunResult.class);
        util.exportExcel(response, list, "测试流运行数据");
    }

    /**
     * 查询单次 Run 详情（头信息、已落库步骤、图快照）。
     * 执行中也可调用，步骤列表随执行推进变长。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping(value = "/{testFlowRunId}")
    public R<TestFlowRunDetailResult> getInfo(@PathVariable("testFlowRunId") Long testFlowRunId) {
        return ok(testFlowExecutionService.getRunDetail(testFlowRunId));
    }

    /**
     * 下载单次运行的简易 HTML 报告。
     * 将报告作为附件写入响应流；文件名含项目、流名、场景、状态、时间与运行 ID。
     * 仅成功、失败、中止、取消状态的运行可导出。
     *
     * @param testFlowRunId 运行 ID
     * @param response      HTTP 响应
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @Log(title = "测试流运行报告", businessType = BusinessType.EXPORT)
    @GetMapping("/{testFlowRunId}/report.html")
    public void exportHtmlReport(@PathVariable("testFlowRunId") Long testFlowRunId,
                                 HttpServletResponse response) throws Exception {
        var report = testFlowRunReportService.buildHtmlReport(testFlowRunId);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        FileUtils.setAttachmentResponseHeader(response, report.getFileName());
        response.getOutputStream().write(report.getHtml().getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    /**
     * 触发测试流 Run。
     * 校验图与就绪条件 → 写入 running 记录 → 立刻返回 runId；图在后台异步执行，每步完成即落库。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试流运行", businessType = BusinessType.OTHER)
    @PostMapping("/trigger")
    public R<String> trigger(@RequestBody TriggerTestFlowRunParams params) {
        Long runId = testFlowExecutionService.triggerRun(params);
        return ok(runId != null ? String.valueOf(runId) : null);
    }

    /**
     * 恢复 paused 状态的 Run：还原/重试/跳过/中止。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试流运行", businessType = BusinessType.OTHER)
    @PostMapping("/{testFlowRunId}/resume")
    public R<ResumeRunResult> resume(@PathVariable Long testFlowRunId,
                                     @RequestBody ResumeTestFlowRunParams params) {
        return ok(testFlowExecutionService.resumeRun(testFlowRunId, params));
    }

    /**
     * 删除测试流运行
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试流运行", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testFlowRunIds}")
    public R<Void> remove(@PathVariable Long[] testFlowRunIds) {
        List<Long> idList = Convert.toLongList(testFlowRunIds);
        return toR(testFlowRunService.logicDeleteTestFlowRunByIdList(idList));
    }
}
