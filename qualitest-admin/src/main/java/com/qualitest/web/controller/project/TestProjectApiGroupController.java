package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.project.domain.TestProjectApiGroup;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.params.TestProjectApiGroupParams;
import com.qualitest.project.result.TestProjectApiGroupResult;
import com.qualitest.project.service.ITestProjectApiGroupService;
import com.qualitest.project.service.ITestProjectMemberService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试项目API分组Controller
 *
 * @author qualitest
 * @date 2026-02-05
 */
@RestController
@RequestMapping("/project/testProjectApiGroup")
@AllArgsConstructor
public class TestProjectApiGroupController extends BaseController {

    private final ITestProjectApiGroupService testProjectApiGroupService;
    private final ITestProjectMemberService testProjectMemberService;

    /**
     * 查询测试项目 API 分组列表；指定项目时需查看接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestProjectApiGroupParams params) {
        if (params.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(params.getTestProjectId(), TestProjectMemberPerm.API_VIEW);
        }
        startPage();
        List<TestProjectApiGroupResult> list = testProjectApiGroupService.selectTestProjectApiGroupResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出测试项目 API 分组列表；指定项目时需查看接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:export')")
    @Log(title = "测试项目API分组", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TestProjectApiGroupParams params) {
        if (params.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(params.getTestProjectId(), TestProjectMemberPerm.API_VIEW);
        }
        List<TestProjectApiGroupResult> list = testProjectApiGroupService.selectTestProjectApiGroupResultList(params);
        ExcelUtil<TestProjectApiGroupResult> util = new ExcelUtil<>(TestProjectApiGroupResult.class);
        util.exportExcel(response, list, "测试项目API分组数据");
    }

    /**
     * 获取测试项目 API 分组详细信息；需查看接口权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping(value = "/{apiGroupId}")
    public R<TestProjectApiGroupResult> getInfo(@PathVariable("apiGroupId") Long apiGroupId) {
        TestProjectApiGroupResult result = testProjectApiGroupService.selectTestProjectApiGroupResult(apiGroupId);
        if (result != null && result.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(result.getTestProjectId(), TestProjectMemberPerm.API_VIEW);
        }
        return ok(result);
    }

    /**
     * 新增测试项目 API 分组；需编辑接口与分组权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:add')")
    @Log(title = "测试项目API分组", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TestProjectApiGroup testProjectApiGroup) {
        if (testProjectApiGroup.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(
                    testProjectApiGroup.getTestProjectId(), TestProjectMemberPerm.API_EDIT);
        }
        return toR(testProjectApiGroupService.insertTestProjectApiGroup(testProjectApiGroup));
    }

    /**
     * 修改测试项目 API 分组；需编辑接口与分组权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试项目API分组", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestProjectApiGroup testProjectApiGroup) {
        Long projectId = testProjectApiGroup.getTestProjectId();
        if (projectId == null && testProjectApiGroup.getApiGroupId() != null) {
            TestProjectApiGroupResult existing = testProjectApiGroupService.selectTestProjectApiGroupResult(
                    testProjectApiGroup.getApiGroupId());
            if (existing != null) {
                projectId = existing.getTestProjectId();
            }
        }
        if (projectId != null) {
            testProjectMemberService.requireCapability(projectId, TestProjectMemberPerm.API_EDIT);
        }
        return toR(testProjectApiGroupService.updateTestProjectApiGroup(testProjectApiGroup));
    }

    /**
     * 删除测试项目 API 分组；需编辑接口与分组权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试项目API分组", businessType = BusinessType.DELETE)
    @DeleteMapping("/{apiGroupIds}")
    public R<Void> remove(@PathVariable Long[] apiGroupIds) {
        List<Long> idList = Convert.toLongList(apiGroupIds);
        for (Long id : idList) {
            TestProjectApiGroupResult existing = testProjectApiGroupService.selectTestProjectApiGroupResult(id);
            if (existing != null && existing.getTestProjectId() != null) {
                testProjectMemberService.requireCapability(
                        existing.getTestProjectId(), TestProjectMemberPerm.API_EDIT);
            }
        }
        return toR(testProjectApiGroupService.logicDeleteTestProjectApiGroupByIdList(idList));
    }
}
