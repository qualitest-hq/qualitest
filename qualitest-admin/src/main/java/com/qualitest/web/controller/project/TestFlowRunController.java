package com.qualitest.web.controller.project;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.file.FileUtils;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.params.TestFlowRunParams;
import com.qualitest.project.params.ResumeTestFlowRunParams;
import com.qualitest.project.params.TriggerTestFlowRunBatchParams;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.result.ResumeRunResult;
import com.qualitest.project.result.TestFlowRunDetailResult;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TriggerTestFlowRunBatchResult;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowRunReportService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectMemberService;
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
    private final ITestFlowService testFlowService;
    private final ITestProjectMemberService testProjectMemberService;

    /**
     * 查询测试流运行列表；须指定测试流，且需查看测试流与跑流记录权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestFlowRunParams params) {
        requireFlowRunViewByFlowId(params != null ? params.getTestFlowId() : null);
        startPage();
        List<TestFlowRunResult> list = testFlowRunService.selectTestFlowRunResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出测试流运行列表；须指定测试流，且需查看测试流与跑流记录权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:export')")
    @Log(title = "测试流运行", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TestFlowRunParams params) {
        requireFlowRunViewByFlowId(params != null ? params.getTestFlowId() : null);
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
     * 批量串行触发：按队列逐条 trigger 并等待离开 running，再处理下一项。
     * 适合短批或服务端调用；长批建议客户端逐条 trigger 并轮询，以免 HTTP 超时。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试流批量运行", businessType = BusinessType.OTHER)
    @PostMapping("/triggerBatch")
    public R<TriggerTestFlowRunBatchResult> triggerBatch(@RequestBody TriggerTestFlowRunBatchParams params) {
        return ok(testFlowExecutionService.triggerBatch(params));
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
     * 删除测试流运行；按记录所属项目校验查看测试流与跑流记录权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试流运行", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testFlowRunIds}")
    public R<Void> remove(@PathVariable Long[] testFlowRunIds) {
        List<Long> idList = Convert.toLongList(testFlowRunIds);
        requireFlowRunViewByRunIds(idList);
        return toR(testFlowRunService.logicDeleteTestFlowRunByIdList(idList));
    }

    /**
     * 按测试流校验「查看跑流记录」权限；未指定流则拒绝（避免无项目范围扫全表）。
     */
    private void requireFlowRunViewByFlowId(Long testFlowId) {
        if (testFlowId == null) {
            throw new ServiceException("请指定测试流");
        }
        TestFlow flow = testFlowService.selectTestFlowById(testFlowId);
        if (flow == null || flow.getTestProjectId() == null) {
            throw new ServiceException("测试流不存在");
        }
        testProjectMemberService.requireCapability(flow.getTestProjectId(), TestProjectMemberPerm.FLOW_VIEW);
    }

    /**
     * 按运行记录反查所属项目并校验查看权限。
     */
    private void requireFlowRunViewByRunIds(List<Long> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            throw new ServiceException("请选择要删除的运行记录");
        }
        Set<Long> projectIds = new HashSet<>();
        for (Long runId : runIds) {
            TestFlowRun run = testFlowRunService.selectTestFlowRunById(runId);
            if (run == null || run.getTestFlowId() == null) {
                throw new ServiceException("运行记录不存在");
            }
            TestFlow flow = testFlowService.selectTestFlowById(run.getTestFlowId());
            if (flow == null || flow.getTestProjectId() == null) {
                throw new ServiceException("测试流不存在");
            }
            projectIds.add(flow.getTestProjectId());
        }
        for (Long projectId : projectIds) {
            testProjectMemberService.requireCapability(projectId, TestProjectMemberPerm.FLOW_VIEW);
        }
    }
}
