package com.qualitest.web.controller.project;

import com.qualitest.common.annotation.Log;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.domain.entity.SysUser;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.common.core.text.Convert;
import com.qualitest.common.enums.BusinessType;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.common.utils.poi.ExcelUtil;
import com.qualitest.project.domain.TestProjectMember;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.params.TestProjectMemberParams;
import com.qualitest.project.result.TestProjectMemberResult;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.system.service.ISysUserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 测试项目成员Controller
 *
 * @author qualitest
 * @date 2026-02-05
 */
@RestController
@RequestMapping("/project/testProjectMember")
@AllArgsConstructor
public class TestProjectMemberController extends BaseController {

    private final ITestProjectMemberService testProjectMemberService;
    private final ISysUserService userService;

    /**
     * 查询测试项目成员列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:list')")
    @GetMapping("/list")
    public TableDataInfo list(@Valid TestProjectMemberParams params) {
        startPage();
        List<TestProjectMemberResult> list = testProjectMemberService.selectTestProjectMemberResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出测试项目成员列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:export')")
    @Log(title = "测试项目成员", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Valid TestProjectMemberParams params) {
        List<TestProjectMemberResult> list = testProjectMemberService.selectTestProjectMemberResultList(params);
        ExcelUtil<TestProjectMemberResult> util = new ExcelUtil<>(TestProjectMemberResult.class);
        util.exportExcel(response, list, "测试项目成员数据");
    }

    /**
     * 获取测试项目成员详细信息
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:query')")
    @GetMapping(value = "/{testProjectMemberId}")
    public R<TestProjectMemberResult> getInfo(@PathVariable("testProjectMemberId") Long testProjectMemberId) {
        return ok(testProjectMemberService.selectTestProjectMemberResult(testProjectMemberId));
    }

    /**
     * 分页查询可加入项目的用户
     *
     * @param user          查询条件
     * @param testProjectId 测试项目ID
     * @param retainUserId  可选；若传入则从排除列表中去掉该用户
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:add')")
    @GetMapping("/selectableUsers")
    public TableDataInfo selectableUsers(
            SysUser user,
            @RequestParam("testProjectId") Long testProjectId,
            @RequestParam(value = "retainUserId", required = false) Long retainUserId
    ) {
        if (testProjectId == null) {
            throw new ServiceException("请指定测试项目");
        }
        TestProjectMemberRole memberRole = testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        assertCanManageMembers(memberRole);
        List<Long> exclude = testProjectMemberService.listMemberUserIdsByTestProjectId(testProjectId);
        if (retainUserId != null) {
            exclude.remove(retainUserId);
        }
        user.setExcludeUserIds(exclude);
        startPage();
        List<SysUser> list = userService.selectUserList(user);
        return getDataTable(list);
    }

    /**
     * 新增测试项目成员
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:add')")
    @Log(title = "测试项目成员", businessType = BusinessType.INSERT)
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Void> add(@RequestBody TestProjectMember testProjectMember) {
        Long testProjectId = testProjectMember.getTestProjectId();
        TestProjectMemberRole loginMemberRole = testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        assertCanManageMembers(loginMemberRole);
        assertProjectAdminMayNotAssignOwner(loginMemberRole, testProjectMember.getMemberRole());
        return toR(testProjectMemberService.insertTestProjectMember(testProjectMember));
    }

    /**
     * 修改测试项目成员
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:edit')")
    @Log(title = "测试项目成员", businessType = BusinessType.UPDATE)
    @PutMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Void> edit(@RequestBody TestProjectMember testProjectMember) {
        TestProjectMember existing = testProjectMemberService.selectTestProjectMemberById(testProjectMember.getTestProjectMemberId());
        if (existing == null) {
            throw new ServiceException("成员不存在");
        }
        TestProjectMemberRole loginMemberRole = testProjectMemberService.getCheckProjectMemberRole(existing.getTestProjectId());
        assertCanManageMembers(loginMemberRole);
        testProjectMember.setTestProjectId(existing.getTestProjectId());
        testProjectMember.setUserId(existing.getUserId());
        String newRole = testProjectMember.getMemberRole();
        if (newRole == null || newRole.isEmpty()) {
            newRole = existing.getMemberRole();
        }
        assertProjectAdminMayManageTarget(loginMemberRole, existing, newRole);
        return toR(testProjectMemberService.updateTestProjectMember(testProjectMember));
    }

    /**
     * 删除测试项目成员
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:remove')")
    @Log(title = "测试项目成员", businessType = BusinessType.DELETE)
    @DeleteMapping("/{testProjectMemberIds}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> remove(@PathVariable Long[] testProjectMemberIds) {
        List<Long> idList = Convert.toLongList(testProjectMemberIds);
        Set<Long> managedProjectIds = new HashSet<>();
        for (Long id : idList) {
            TestProjectMember member = testProjectMemberService.selectTestProjectMemberById(id);
            if (member == null) {
                continue;
            }
            Long testProjectId = member.getTestProjectId();
            TestProjectMemberRole loginMemberRole = testProjectMemberService.getCheckProjectMemberRole(testProjectId);
            if (managedProjectIds.add(testProjectId)) {
                assertCanManageMembers(loginMemberRole);
            }
            // 每条都校验：项目管理员不能删所有者 / 其他管理员
            assertProjectAdminMayManageTarget(loginMemberRole, member, member.getMemberRole());
        }
        return toR(testProjectMemberService.logicDeleteTestProjectMemberByIdList(idList));
    }

    private void assertCanManageMembers(TestProjectMemberRole loginMemberRole) {
        if (!TestProjectMemberRole.canManageMember(loginMemberRole.getCode())) {
            throw new ServiceException("您无权管理项目成员");
        }
    }

    private void assertProjectAdminMayNotAssignOwner(TestProjectMemberRole loginMemberRole, String memberRole) {
        if (TestProjectMemberRole.ADMIN.equals(loginMemberRole)
                && TestProjectMemberRole.OWNER.getCode().equals(memberRole)) {
            throw new ServiceException("项目管理员不能指定成员为所有者");
        }
    }

    /**
     * 项目管理员：不能操作所有者、不能操作其他管理员、不能指定为所有者。
     */
    private void assertProjectAdminMayManageTarget(
            TestProjectMemberRole loginMemberRole,
            TestProjectMember target,
            String newRole
    ) {
        if (!TestProjectMemberRole.ADMIN.equals(loginMemberRole)) {
            return;
        }
        if (TestProjectMemberRole.OWNER.getCode().equals(target.getMemberRole())) {
            throw new ServiceException("项目管理员不能操作所有者");
        }
        if (TestProjectMemberRole.ADMIN.getCode().equals(target.getMemberRole())
                && !Objects.equals(target.getUserId(), SecurityUtils.getUserId())) {
            throw new ServiceException("项目管理员不能操作其他管理员");
        }
        assertProjectAdminMayNotAssignOwner(loginMemberRole, newRole);
    }
}
