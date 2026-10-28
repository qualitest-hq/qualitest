package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.params.BindTestSuiteRunItemParams;
import com.qualitest.project.params.FinishTestSuiteRunItemParams;
import com.qualitest.project.params.StartTestSuiteRunParams;
import com.qualitest.project.params.TestSuiteRunParams;
import com.qualitest.project.result.TestSuiteRunResult;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.project.service.ITestSuiteRunService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 测试套件运行Controller
 *
 * @author qualitest
 * @date 2026-10-28
 */
@RestController
@RequestMapping("/project/testSuiteRun")
@AllArgsConstructor
public class TestSuiteRunController extends BaseController {

    private final ITestSuiteRunService testSuiteRunService;
    private final ITestProjectMemberService testProjectMemberService;

    /**
     * 查询测试套件运行列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestSuiteRunParams params) {
        if (params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        startPage();
        List<TestSuiteRunResult> list = testSuiteRunService.selectTestSuiteRunResultList(params);
        return getDataTable(list);
    }

    /**
     * 获取测试套件运行详细信息（含项）
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/{testSuiteRunId}")
    public R<TestSuiteRunResult> getInfo(@PathVariable Long testSuiteRunId) {
        TestSuiteRunResult result = testSuiteRunService.selectTestSuiteRunResult(testSuiteRunId);
        if (result != null && result.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(result.getTestProjectId());
        }
        return ok(result);
    }

    /**
     * 开始套件运行：写入 running 头与 pending 项，立刻返回；由客户端串行 trigger 并回写
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试套件运行", businessType = BusinessType.OTHER)
    @PostMapping("/start")
    public R<TestSuiteRunResult> start(@RequestBody StartTestSuiteRunParams params) {
        if (params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        return ok(testSuiteRunService.start(params));
    }

    /**
     * 运行项绑定正式 RunId，状态置为 running
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @PostMapping("/{testSuiteRunId}/items/{testSuiteRunItemId}/bindRun")
    public R<Void> bindRun(@PathVariable Long testSuiteRunId,
                           @PathVariable Long testSuiteRunItemId,
                           @RequestBody BindTestSuiteRunItemParams params) {
        TestSuiteRunResult detail = testSuiteRunService.selectTestSuiteRunResult(testSuiteRunId);
        if (detail != null && detail.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(detail.getTestProjectId());
        }
        testSuiteRunService.bindRun(testSuiteRunId, testSuiteRunItemId, params);
        return ok();
    }

    /**
     * 运行项写入终态并刷新汇总
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @PostMapping("/{testSuiteRunId}/items/{testSuiteRunItemId}/finish")
    public R<TestSuiteRunResult> finishItem(@PathVariable Long testSuiteRunId,
                                            @PathVariable Long testSuiteRunItemId,
                                            @RequestBody FinishTestSuiteRunItemParams params) {
        TestSuiteRunResult detail = testSuiteRunService.selectTestSuiteRunResult(testSuiteRunId);
        if (detail != null && detail.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(detail.getTestProjectId());
        }
        return ok(testSuiteRunService.finishItem(testSuiteRunId, testSuiteRunItemId, params));
    }

    /**
     * 中止队列：未跑项标 skipped，头状态 aborted
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试套件运行", businessType = BusinessType.OTHER)
    @PostMapping("/{testSuiteRunId}/abort")
    public R<TestSuiteRunResult> abort(@PathVariable Long testSuiteRunId) {
        TestSuiteRunResult detail = testSuiteRunService.selectTestSuiteRunResult(testSuiteRunId);
        if (detail != null && detail.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(detail.getTestProjectId());
        }
        return ok(testSuiteRunService.abort(testSuiteRunId));
    }
}
