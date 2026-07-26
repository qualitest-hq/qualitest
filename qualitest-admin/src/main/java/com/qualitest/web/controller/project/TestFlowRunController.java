package com.qualitest.web.controller.project;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
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
import com.qualitest.project.params.TestFlowRunParams;
import com.qualitest.project.params.ResumeTestFlowRunParams;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.result.ResumeRunResult;
import com.qualitest.project.result.TestFlowRunDetailResult;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.common.core.page.TableDataInfo;

/**
 * 测试流运行 HTTP 接口。
 * <p>
 * 业务入口：{@link #trigger} 触发同步执行；{@link #getInfo} 查询 Run 报告（含步骤与图快照）。
 */
@RestController
@RequestMapping("/project/testFlowRun")
@AllArgsConstructor
public class TestFlowRunController extends BaseController {

    private final ITestFlowRunService testFlowRunService;
    private final ITestFlowExecutionService testFlowExecutionService;

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
     * 获取测试流运行详细信息（含 steps + graph_json_snapshot）
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping(value = "/{testFlowRunId}")
    public R<TestFlowRunDetailResult> getInfo(@PathVariable("testFlowRunId") Long testFlowRunId) {
        return ok(testFlowExecutionService.getRunDetail(testFlowRunId));
    }

    /**
     * 触发测试流 Run：校验图 → 固化 snapshot → 同步执行 → 返回 testFlowRunId。
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
