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
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.params.ApplyAuthTemplatesParams;
import com.qualitest.project.params.SaveAsAuthTemplateParams;
import com.qualitest.project.params.TestProjectParams;
import com.qualitest.project.result.ProjectRunHubResult;
import com.qualitest.project.result.TestProjectResult;
import com.qualitest.project.result.TestProjectUserContextResult;
import com.qualitest.project.result.TestProjectUserSettingResult;
import com.qualitest.project.service.IProjectRunHubService;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.service.ITestProjectUserSettingService;
import com.qualitest.project.support.ProjectAuthTemplateApplyService;
import com.qualitest.project.support.templatepack.ProjectTemplateFromProjectService;
import com.qualitest.project.support.templatepack.ProjectTemplatePackModels.PackOpResult;
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
    private final ProjectTemplateFromProjectService projectTemplateFromProjectService;
    private final IProjectRunHubService projectRunHubService;

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
     * 项目运行中心总览。
     * 返回当前并发模式、缓存中的排队/执行中活动项，以及库表最近正式 Run。
     * 只需能查项目，不要求编辑权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/{testProjectId}/runHub")
    public R<ProjectRunHubResult> runHub(
            @PathVariable Long testProjectId,
            @RequestParam(value = "recentLimit", required = false) Integer recentLimit) {
        return ok(projectRunHubService.getProjectRunHub(testProjectId, recentLimit));
    }

    /**
     * 取消仍在排队、尚未开跑的项。
     * 从环境队列移除，并打断对该 queueItemId 的等待轮询。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @DeleteMapping("/{testProjectId}/runHub/queue/{queueItemId}")
    public R<Void> cancelQueuedRun(
            @PathVariable Long testProjectId, @PathVariable String queueItemId) {
        return toR(projectRunHubService.cancelQueuedRun(testProjectId, queueItemId) ? 1 : 0);
    }

    /**
     * 非阻塞查询排队项是否已开跑。
     * data 为 runId 字符串；尚未开跑或已取消时 data 为空。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/{testProjectId}/runHub/queue/{queueItemId}/runId")
    public R<String> peekQueuedRunId(
            @PathVariable Long testProjectId, @PathVariable String queueItemId) {
        testProjectMemberService.requireCapability(testProjectId, TestProjectMemberPerm.FLOW_VIEW);
        Long runId = projectRunHubService.peekQueuedRunId(queueItemId);
        return ok(runId != null ? String.valueOf(runId) : null);
    }

    /**
     * 从运行中心活动列表移除一行。
     * 排队行等价于取消排队；执行中行只清缓存展示，不删库表正式报告、不中止后台 Run。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @DeleteMapping("/{testProjectId}/runHub/active/{itemId}")
    public R<Void> dismissHubActive(
            @PathVariable Long testProjectId, @PathVariable String itemId) {
        projectRunHubService.dismissHubActiveItem(testProjectId, itemId);
        return ok();
    }

    /**
     * 新增测试项目。
     * 顺序：插项目 → 建 Owner/默认环境 → 再 Apply 模板（预制环境依赖已有环境行，才能把占位 URL 写成 demo）。
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
        List<Long> templateIds = testProject.getTemplateIds();
        if (templateIds != null && !templateIds.isEmpty()) {
            projectAuthTemplateApplyService.apply(testProject.getTestProjectId(), templateIds);
        }
        return ok();
    }

    /**
     * 修改测试项目；需项目成员具备编辑项目设置权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试项目", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TestProject testProject) {
        if (testProject.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(
                    testProject.getTestProjectId(), TestProjectMemberPerm.PROJECT_EDIT);
        }
        return toR(testProjectService.updateTestProject(testProject));
    }

    /**
     * 从项目模板追加鉴权 Profile，并种子尚未存在的预制接口；需编辑项目设置权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:edit')")
    @Log(title = "测试项目模板", businessType = BusinessType.UPDATE)
    @PostMapping("/{testProjectId}/applyAuthTemplates")
    public R<Void> applyAuthTemplates(
            @PathVariable Long testProjectId,
            @RequestBody ApplyAuthTemplatesParams params) {
        testProjectMemberService.requireCapability(testProjectId, TestProjectMemberPerm.PROJECT_EDIT);
        projectAuthTemplateApplyService.apply(
                testProjectId, params != null ? params.getTemplateIds() : null);
        return ok();
    }

    /**
     * 从本项目另存为自定义鉴权项目模板（完整包：流关联接口/素材必带，其余可追加）。
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectTemplate:add')")
    @Log(title = "另存项目模板", businessType = BusinessType.INSERT)
    @PostMapping("/{testProjectId}/saveAsAuthTemplate")
    public R<PackOpResult> saveAsAuthTemplate(
            @PathVariable Long testProjectId,
            @RequestBody SaveAsAuthTemplateParams params) {
        return ok(projectTemplateFromProjectService.saveAsAuthTemplate(testProjectId, params));
    }

    /**
     * 删除测试项目；逐项校验删除项目权限。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:remove')")
    @Log(title = "测试项目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testProjectIds}")
    public R<Void> remove(@PathVariable Long[] testProjectIds) {
        List<Long> idList = Convert.toLongList(testProjectIds);
        for (Long id : idList) {
            testProjectMemberService.requireCapability(id, TestProjectMemberPerm.PROJECT_DELETE);
        }
        return toR(testProjectService.logicDeleteTestProjectByIdList(idList));
    }

    /**
     * 返回当前用户在项目下的角色、权限列表与个人设置。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/myProjectContext")
    public R<TestProjectUserContextResult> myProjectContext(@RequestParam("testProjectId") Long testProjectId) {
        // 登录用户ID
        Long userId = SecurityUtils.getUserId();
        // 项目成员
        String memberRole = testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        java.util.Set<String> capabilities = testProjectMemberService.listCapabilities(testProjectId);
        TestProjectUserSetting ensured =
                testProjectUserSettingService.getUserSettingForProjectSetting(testProjectId, userId);
        TestProjectUserSettingResult setting = testProjectUserSettingService.selectTestProjectUserSettingResult(
                ensured.getTestProjectUserSettingId());
        return ok(TestProjectUserContextResult.builder()
                .testProjectId(testProjectId)
                .userId(userId)
                .memberRole(memberRole)
                .capabilities(new java.util.ArrayList<>(capabilities))
                .setting(setting)
                .build());
    }

}
