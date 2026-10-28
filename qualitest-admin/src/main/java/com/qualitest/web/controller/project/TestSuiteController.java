package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.params.TestSuiteParams;
import com.qualitest.project.params.TestSuiteSaveParams;
import com.qualitest.project.result.TestSuiteResult;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.project.service.ITestSuiteService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 测试套件Controller
 *
 * @author qualitest
 * @date 2026-10-28
 */
@RestController
@RequestMapping("/project/testSuite")
@AllArgsConstructor
public class TestSuiteController extends BaseController {

    private final ITestSuiteService testSuiteService;
    private final ITestProjectMemberService testProjectMemberService;

    /**
     * 查询测试套件列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestSuiteParams params) {
        if (params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        startPage();
        List<TestSuiteResult> list = testSuiteService.selectTestSuiteResultList(params);
        return getDataTable(list);
    }

    /**
     * 获取测试套件详细信息（含项）
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/{testSuiteId}")
    public R<TestSuiteResult> getInfo(@PathVariable Long testSuiteId) {
        TestSuiteResult result = testSuiteService.selectTestSuiteDetail(testSuiteId);
        if (result != null && result.getTestProjectId() != null) {
            testProjectMemberService.getCheckProjectMemberRole(result.getTestProjectId());
        }
        return ok(result);
    }

    /**
     * 新增测试套件
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:add')")
    @Log(title = "测试套件", businessType = BusinessType.INSERT)
    @PostMapping
    public R<String> add(@RequestBody TestSuiteSaveParams params) {
        if (params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        Long id = testSuiteService.saveTestSuite(params);
        return ok(id != null ? String.valueOf(id) : null);
    }

    /**
     * 修改测试套件（项整表替换）
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试套件", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<String> edit(@RequestBody TestSuiteSaveParams params) {
        if (params.getTestSuiteId() == null) {
            throw new ServiceException("testSuiteId 不能为空");
        }
        if (params.getTestProjectId() == null) {
            throw new ServiceException("testProjectId 不能为空");
        }
        testProjectMemberService.getCheckProjectMemberRole(params.getTestProjectId());
        Long id = testSuiteService.saveTestSuite(params);
        return ok(id != null ? String.valueOf(id) : null);
    }

    /**
     * 删除测试套件
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试套件", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testSuiteIds}")
    public R<Void> remove(@PathVariable Long[] testSuiteIds) {
        List<Long> idList = Convert.toLongList(testSuiteIds);
        for (Long id : idList) {
            TestSuiteResult existing = testSuiteService.selectTestSuiteDetail(id);
            if (existing != null && existing.getTestProjectId() != null) {
                testProjectMemberService.getCheckProjectMemberRole(existing.getTestProjectId());
            }
        }
        return toR(testSuiteService.logicDeleteTestSuiteByIdList(idList));
    }
}
