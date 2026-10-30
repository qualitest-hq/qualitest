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
import com.qualitest.project.enums.TestProjectMemberPerm;
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

    /** 项目成员业务 */
    private final ITestProjectMemberService testProjectMemberService;

    /** 系统用户查询 */
    private final ISysUserService userService;

    /**
     * 查询测试项目成员列表；需具备查看成员权限。
     *
     * @param params 查询条件
     * @return 分页列表
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:list')")
    @GetMapping("/list")
    public TableDataInfo list(@Valid TestProjectMemberParams params) {
        if (params.getTestProjectId() == null) {
            throw new ServiceException("请指定测试项目");
        }
        testProjectMemberService.requireCapability(params.getTestProjectId(), TestProjectMemberPerm.MEMBER_VIEW);
        startPage();
        List<TestProjectMemberResult> list = testProjectMemberService.selectTestProjectMemberResultList(params);
        return getDataTable(list);
    }

    /**
     * 导出测试项目成员列表；需具备查看成员权限。
     *
     * @param response 响应流
     * @param params 查询条件
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:export')")
    @Log(title = "测试项目成员", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, @Valid TestProjectMemberParams params) {
        if (params.getTestProjectId() == null) {
            throw new ServiceException("请指定测试项目");
        }
        testProjectMemberService.requireCapability(params.getTestProjectId(), TestProjectMemberPerm.MEMBER_VIEW);
        List<TestProjectMemberResult> list = testProjectMemberService.selectTestProjectMemberResultList(params);
        ExcelUtil<TestProjectMemberResult> util = new ExcelUtil<>(TestProjectMemberResult.class);
        util.exportExcel(response, list, "测试项目成员数据");
    }

    /**
     * 获取测试项目成员详细信息；需具备查看成员权限。
     *
     * @param testProjectMemberId 成员主键
     * @return 成员详情
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:query')")
    @GetMapping(value = "/{testProjectMemberId}")
    public R<TestProjectMemberResult> getInfo(@PathVariable("testProjectMemberId") Long testProjectMemberId) {
        TestProjectMemberResult result = testProjectMemberService.selectTestProjectMemberResult(testProjectMemberId);
        if (result != null && result.getTestProjectId() != null) {
            testProjectMemberService.requireCapability(result.getTestProjectId(), TestProjectMemberPerm.MEMBER_VIEW);
        }
        return ok(result);
    }

    /**
     * 分页查询可加入项目的用户；需添加成员权限。
     *
     * @param user          查询条件
     * @param testProjectId 测试项目ID
     * @param retainUserId  可选；保留在候选列表中的用户 id
     * @return 分页用户列表
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
        testProjectMemberService.requireCapability(testProjectId, TestProjectMemberPerm.MEMBER_ADD);
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
     * 新增测试项目成员；需具备添加成员权限。
     * 新指定为所有者时还需具备转让所有者权限；非所有者不能操作所有者或其他管理员，项目管理员不能指定为所有者。
     *
     * @param testProjectMember 成员信息
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('project:testProjectMember:add')")
    @Log(title = "测试项目成员", businessType = BusinessType.INSERT)
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Void> add(@RequestBody TestProjectMember testProjectMember) {
        Long testProjectId = testProjectMember.getTestProjectId();
        String loginMemberRole = testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        testProjectMemberService.requireCapability(testProjectId, TestProjectMemberPerm.MEMBER_ADD);
        requireTransferIfAssigningOwner(testProjectId, null, testProjectMember.getMemberRole());
        assertNonOwnerMayManageTarget(loginMemberRole, null, testProjectMember.getMemberRole());
        return toR(testProjectMemberService.insertTestProjectMember(testProjectMember));
    }

    /**
     * 修改测试项目成员；需具备编辑成员权限。
     * 升为所有者时还需具备转让所有者权限；非所有者不能操作所有者、不能操作其他管理员、项目管理员不能指定为所有者。
     *
     * @param testProjectMember 成员信息
     * @return 操作结果
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
        String loginMemberRole = testProjectMemberService.getCheckProjectMemberRole(existing.getTestProjectId());
        testProjectMemberService.requireCapability(existing.getTestProjectId(), TestProjectMemberPerm.MEMBER_EDIT);
        testProjectMember.setTestProjectId(existing.getTestProjectId());
        testProjectMember.setUserId(existing.getUserId());
        String newRole = testProjectMember.getMemberRole();
        if (newRole == null || newRole.isEmpty()) {
            newRole = existing.getMemberRole();
        }
        requireTransferIfAssigningOwner(existing.getTestProjectId(), existing.getMemberRole(), newRole);
        assertNonOwnerMayManageTarget(loginMemberRole, existing, newRole);
        return toR(testProjectMemberService.updateTestProjectMember(testProjectMember));
    }

    /**
     * 删除测试项目成员；每个涉及的项目需具备移除成员权限。
     * 非所有者不能删除所有者或其他管理员。
     *
     * @param testProjectMemberIds 成员主键数组
     * @return 操作结果
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
            String loginMemberRole = testProjectMemberService.getCheckProjectMemberRole(testProjectId);
            if (managedProjectIds.add(testProjectId)) {
                testProjectMemberService.requireCapability(testProjectId, TestProjectMemberPerm.MEMBER_REMOVE);
            }
            // 校验操作者与目标成员的层级约束
            assertNonOwnerMayManageTarget(loginMemberRole, member, member.getMemberRole());
        }
        return toR(testProjectMemberService.logicDeleteTestProjectMemberByIdList(idList));
    }

    /**
     * 新升为所有者时需具备转让权限。
     *
     * @param testProjectId 项目ID
     * @param oldRole       原角色；新增时为 null
     * @param newRole       目标角色
     */
    private void requireTransferIfAssigningOwner(Long testProjectId, String oldRole, String newRole) {
        if (!TestProjectMemberRole.isOwner(newRole)) {
            return;
        }
        if (TestProjectMemberRole.isOwner(oldRole)) {
            return;
        }
        testProjectMemberService.requireCapability(testProjectId, TestProjectMemberPerm.PROJECT_TRANSFER);
    }

    /**
     * 非所有者/系统管理员：不能操作所有者、不能操作其他管理员；项目管理员不能指定为所有者。
     *
     * @param loginMemberRole 当前登录者在项目中的角色
     * @param target          目标成员；新增时为 null
     * @param newRole         目标角色（新增指定或修改后的角色）
     */
    private void assertNonOwnerMayManageTarget(
            String loginMemberRole,
            TestProjectMember target,
            String newRole
    ) {
        if (TestProjectMemberRole.isOwner(loginMemberRole)
                || TestProjectMemberRole.isSysAdmin(loginMemberRole)) {
            return;
        }
        if (target != null && TestProjectMemberRole.isOwner(target.getMemberRole())) {
            throw new ServiceException("不能操作所有者");
        }
        if (target != null
                && TestProjectMemberRole.isAdmin(target.getMemberRole())
                && !Objects.equals(target.getUserId(), SecurityUtils.getUserId())) {
            throw new ServiceException("不能操作其他管理员");
        }
        if (TestProjectMemberRole.isAdmin(loginMemberRole)
                && TestProjectMemberRole.isOwner(newRole)) {
            throw new ServiceException("项目管理员不能指定成员为所有者");
        }
    }
}
