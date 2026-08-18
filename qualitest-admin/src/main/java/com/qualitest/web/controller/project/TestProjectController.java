package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.domain.TestProjectMember;
import com.qualitest.project.domain.TestProjectUserSetting;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.params.ApplyAuthTemplatesParams;
import com.qualitest.project.params.TestProjectParams;
import com.qualitest.project.result.TestProjectResult;
import com.qualitest.project.result.TestProjectUserContextResult;
import com.qualitest.project.result.TestProjectUserSettingResult;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.service.ITestProjectUserSettingService;
import com.qualitest.project.support.ProjectAuthTemplateApplyService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试项目Controller
 *
 * @author qualitest
 * @date 2026-02-05
 */
@RestController
@RequestMapping("/project/testProject")
@AllArgsConstructor
public class TestProjectController extends BaseController {

    private final ITestProjectService testProjectService;
    private final ITestProjectMemberService testProjectMemberService;
    private final ITestProjectUserSettingService testProjectUserSettingService;
    private final ProjectAuthTemplateApplyService projectAuthTemplateApplyService;

    /**
     * 查询测试项目列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:list')")
    @GetMapping("/list")
    public TableDataInfo list(TestProjectParams params) {
        // 非管理员时，只能查看自己加入的项目
        if (!isAdmin()) {
            params.setMemberUserId(getUserId());
        }
        startPage();
        List<TestProjectResult> list = testProjectService.selectTestProjectResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出测试项目列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:export')")
    @Log(title = "测试项目", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TestProjectParams params) {
        // 非管理员时，只能查看自己加入的项目
        if (!isAdmin()) {
            params.setMemberUserId(getUserId());
        }
        List<TestProjectResult> list = testProjectService.selectTestProjectResultList(params);
        ExcelUtil<TestProjectResult> util = new ExcelUtil<>(TestProjectResult.class);
        util.exportExcel(response, list, "测试项目数据");
    }

    /**
     * 获取测试项目详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping(value = "/{testProjectId}")
    public R<TestProjectResult> getInfo(@PathVariable("testProjectId") Long testProjectId) {
        return ok(testProjectService.selectTestProjectResult(testProjectId));
    }

    /**
     * 新增测试项目
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:add')")
    @Log(title = "测试项目", businessType = BusinessType.INSERT)
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Void> add(@RequestBody TestProject testProject) {
        Long userId = getUserId();
        testProject.setOwnerId(userId);
        int projectFlag = testProjectService.insertTestProject(testProject);
        if (projectFlag <= 0) {
            throw new RuntimeException("新增项目失败");
        }
        int ownerFlag = testProjectMemberService.insertTestProjectMember(TestProjectMember.builder()
                .testProjectId(testProject.getTestProjectId())
                .userId(userId)
                .memberRole(TestProjectMemberRole.OWNER.getCode())
                .build());
        if (ownerFlag <= 0) {
            throw new RuntimeException("新增项目成员失败");
        }
        return ok();
    }

    /**
     * 修改测试项目
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试项目", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestProject testProject) {
        return toR(testProjectService.updateTestProject(testProject));
    }

    /**
     * 从模板库追加鉴权 Profile，并种子尚未存在的预制接口。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试项目鉴权模板", businessType = BusinessType.UPDATE)
    @PostMapping("/{testProjectId}/applyAuthTemplates")
    public R<Void> applyAuthTemplates(
            @PathVariable Long testProjectId,
            @RequestBody ApplyAuthTemplatesParams params) {
        projectAuthTemplateApplyService.apply(
                testProjectId, params != null ? params.getTemplateIds() : null);
        return ok();
    }

    /**
     * 删除测试项目
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试项目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testProjectIds}")
    public R<Void> remove(@PathVariable Long[] testProjectIds) {
        List<Long> idList = Convert.toLongList(testProjectIds);
        return toR(testProjectService.logicDeleteTestProjectByIdList(idList));
    }

    /**
     * 我的项目信息
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/myProjectContext")
    public R<TestProjectUserContextResult> myProjectContext(@RequestParam("testProjectId") Long testProjectId) {
        // 登录用户ID
        Long userId = SecurityUtils.getUserId();
        // 项目成员
        TestProjectMemberRole memberRole = testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        TestProjectUserSetting ensured =
                testProjectUserSettingService.getUserSettingForProjectSetting(testProjectId, userId);
        TestProjectUserSettingResult setting = testProjectUserSettingService.selectTestProjectUserSettingResult(
                ensured.getTestProjectUserSettingId());
        return ok(TestProjectUserContextResult.builder()
                .testProjectId(testProjectId)
                .userId(userId)
                .memberRole(memberRole.getCode())
                .setting(setting)
                .build());
    }

}
