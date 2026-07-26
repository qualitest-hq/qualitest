package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestProjectUserSetting;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.params.TestProjectUserSettingSubmitParams;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.project.service.ITestProjectUserSettingService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 测试项目用户设置Controller
 *
 * @author qualitest
 * @date 2026-02-09
 */
@RestController
@RequestMapping("/project/testProjectUserSetting")
@AllArgsConstructor
public class TestProjectUserSettingController extends BaseController {

    private final ITestProjectUserSettingService testProjectUserSettingService;
    private final ITestProjectMemberService testProjectMemberService;

    /**
     * 编辑测试项目用户设置
     */
    @PreAuthorize("@ss.hasAnyPermi('project:testProject:query,project:testProject:edit')")
    @Log(title = "编辑测试项目用户设置", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    public R<Void> edit(@RequestBody TestProjectUserSettingSubmitParams submitParams) {
        Long userId = getUserId();
        Long testProjectId = submitParams.getTestProjectId();
        testProjectMemberService.getCheckProjectMemberRole(testProjectId);

        TestProjectUserSetting existing = testProjectUserSettingService.getUserSettingForProjectSetting(testProjectId, userId);
        if (!userId.equals(existing.getUserId())) {
            throw new ServiceException("无权修改该数据");
        }
        existing.setTestProjectEnvId(submitParams.getTestProjectEnvId());
        existing.setUpdateTime(DateUtils.getNowDate());
        return toR(testProjectUserSettingService.updateTestProjectUserSetting(existing));
    }

    /**
     * 更新测试项目用户设置的环境ID
     */
    @PreAuthorize("@ss.hasAnyPermi('project:testProject:query,project:testProject:edit')")
    @Log(title = "更新测试环境", businessType = BusinessType.UPDATE)
    @PutMapping("/updateEnv")
    public R<Void> updateEnv(@RequestBody TestProjectUserSettingSubmitParams submitParams) {
        Long userId = getUserId();
        Long testProjectId = submitParams.getTestProjectId();
        testProjectMemberService.getCheckProjectMemberRole(testProjectId);

        TestProjectUserSetting existing = testProjectUserSettingService.getUserSettingForProjectSetting(testProjectId, userId);
        if (!userId.equals(existing.getUserId())) {
            throw new ServiceException("无权修改该数据");
        }

        existing.setTestProjectEnvId(submitParams.getTestProjectEnvId());
        existing.setUpdateTime(DateUtils.getNowDate());
        return toR(testProjectUserSettingService.updateTestProjectUserSetting(existing));
    }

    /**
     * 根据项目ID刷新项目Token
     */
    @PreAuthorize("@ss.hasAnyPermi('project:testProject:query,project:testProject:edit')")
    @Log(title = "刷新项目Token", businessType = BusinessType.UPDATE)
    @PostMapping("/refreshToken/project/{testProjectId}")
    public R<String> refreshTokenByProjectId(@PathVariable("testProjectId") Long testProjectId) {
        TestProjectMemberRole memberRole = testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        if (!TestProjectMemberRole.canUseProjectToken(memberRole.getCode())) {
            throw new ServiceException("仅所有者、管理员、开发者可使用项目 Token");
        }
        Long userId = getUserId();

        TestProjectUserSetting setting = testProjectUserSettingService.getUserSettingForProjectSetting(testProjectId, userId);
        if (!userId.equals(setting.getUserId())) {
            throw new ServiceException("无权刷新该Token");
        }

        String newToken = testProjectUserSettingService.refreshProjectToken(setting.getTestProjectUserSettingId());
        return ok(newToken);
    }
}
