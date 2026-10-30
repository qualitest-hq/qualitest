package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.project.constant.TestProjectConstants;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.domain.TestProjectMember;
import com.qualitest.project.domain.TestProjectUserSetting;
import com.qualitest.project.enums.TestProjectMemberPerm;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.mapper.TestProjectMemberMapper;
import com.qualitest.project.params.TestProjectMemberParams;
import com.qualitest.project.result.TestProjectMemberResult;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectMemberRoleService;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.project.service.ITestProjectUserSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 测试项目成员Service业务层处理
 *
 * @author qualitest
 * @date 2026-02-05
 */
@Service
public class TestProjectMemberServiceImpl implements ITestProjectMemberService {

    /** 禁止移除或降级唯一所有者时的提示文案 */
    private static final String KEEP_ONE_OWNER_MSG = "项目必须保留一名所有者，请先将所有权转让给其他成员";

    /** 成员持久化 */
    @Autowired
    private TestProjectMemberMapper testProjectMemberMapper;

    /** 项目主表 */
    @Autowired
    private TestProjectMapper testProjectMapper;

    /** 成员项目内个人设置 */
    @Autowired
    private ITestProjectUserSettingService testProjectUserSettingService;

    /** 成员默认环境 */
    @Autowired
    private ITestProjectEnvService testProjectEnvService;

    /** 角色与权限解析 */
    @Autowired
    @Lazy
    private ITestProjectMemberRoleService testProjectMemberRoleService;

    /**
     * 判断成员角色标识是否为所有者
     *
     * @param memberRole 角色 role_key
     * @return 是所有者时为 true
     */
    private static boolean isOwnerRole(String memberRole) {
        return TestProjectMemberRole.isOwner(memberRole);
    }

    /**
     * 查询测试项目成员列表
     *
     * @param testProjectMember 测试项目成员
     * @return 测试项目成员
     */
    @Override
    public List<TestProjectMember> selectTestProjectMemberList(TestProjectMember testProjectMember) {
        return testProjectMemberMapper.selectTestProjectMemberList(testProjectMember);
    }

    /**
     * 查询测试项目成员
     *
     * @param testProjectMemberId 测试项目成员主键
     * @return 测试项目成员
     */
    @Override
    public TestProjectMember selectTestProjectMemberById(Long testProjectMemberId) {
        return testProjectMemberMapper.selectTestProjectMemberById(testProjectMemberId);
    }

    /**
     * 查询测试项目成员Result列表
     *
     * @param params 测试项目成员Params
     * @return 测试项目成员Result集合
     */
    @Override
    public List<TestProjectMemberResult> selectTestProjectMemberResultList(TestProjectMemberParams params) {
        return testProjectMemberMapper.selectTestProjectMemberResultList(params);
    }

    /**
     * 获取测试项目成员详细信息
     *
     * @param testProjectMemberId 测试项目成员主键
     * @return 测试项目成员Result
     */
    @Override
    public TestProjectMemberResult selectTestProjectMemberResult(Long testProjectMemberId) {
        return testProjectMemberMapper.selectTestProjectMemberResult(testProjectMemberId);
    }

    /**
     * 新增测试项目成员
     *
     * @param testProjectMember 测试项目成员
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestProjectMember(TestProjectMember testProjectMember) {
        if (testProjectMember.getTestProjectId() == null) {
            throw new ServiceException("请指定测试项目");
        }
        if (testProjectMember.getUserId() == null) {
            throw new ServiceException("请选择用户");
        }
        TestProjectMember oldMember = this.selectTestProjectMemberOne(TestProjectMemberParams.builder()
                .testProjectId(testProjectMember.getTestProjectId())
                .userId(testProjectMember.getUserId())
                .build());
        if (oldMember != null) {
            throw new ServiceException("该用户已是本项目成员");
        }
        transferProjectOwner(testProjectMember.getTestProjectId(), testProjectMember.getUserId(),
                testProjectMember.getMemberRole());
        if (testProjectMember.getTestProjectMemberId() == null) {
            testProjectMember.setTestProjectMemberId(IdUtil.getSnowflakeNextId());
        }
        testProjectMember.setCreateTime(DateUtils.getNowDate());
        // 新增成员
        int memberFlag = testProjectMemberMapper.insertTestProjectMember(testProjectMember);
        if (memberFlag <= 0) {
            throw new ServiceException("新增成员失败");
        }
        // 创建默认环境
        TestProjectEnv projectEnv = TestProjectEnv.builder()
                .testProjectId(testProjectMember.getTestProjectId())
                .userId(testProjectMember.getUserId())
                .shareStatus(TestProjectConstants.DEFAULT_ENV_SHARE_STATUS)
                .envName(TestProjectConstants.DEFAULT_ENV_NAME)
                .envUrl(TestProjectConstants.DEFAULT_ENV_URL_PLACEHOLDER)
                .envVariables(TestProjectConstants.EMPTY_ENV_VARIABLES_JSON)
                .build();
        int envFlag = testProjectEnvService.insertTestProjectEnv(projectEnv);
        if (envFlag <= 0) {
            throw new ServiceException("创建默认环境失败");
        }
        // 生成 projectToken（按角色权限 token:use）
        String roleKey = TestProjectMemberRole.normalizeCode(testProjectMember.getMemberRole());
        testProjectMember.setMemberRole(roleKey);
        boolean canToken = testProjectMemberRoleService.listPermCodesByRoleKey(roleKey)
                .contains(TestProjectMemberPerm.TOKEN_USE.getCode());
        String projectToken = canToken
                ? testProjectUserSettingService.generateProjectToken(testProjectMember.getTestProjectId(), testProjectMember.getUserId())
                : null;
        // 创建用户设置
        int settingFlag = testProjectUserSettingService.insertTestProjectUserSetting(TestProjectUserSetting.builder()
                .testProjectId(testProjectMember.getTestProjectId())
                .userId(testProjectMember.getUserId())
                .testProjectEnvId(projectEnv.getTestProjectEnvId())
                .projectToken(projectToken)
                .build());
        if (settingFlag <= 0) {
            throw new ServiceException("创建用户设置失败");
        }
        if (isOwnerRole(testProjectMember.getMemberRole())) {
            assertExactlyOneOwner(testProjectMember.getTestProjectId());
        }
        return memberFlag;
    }

    /**
     * 修改测试项目成员
     *
     * @param testProjectMember 测试项目成员
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestProjectMember(TestProjectMember testProjectMember) {
        TestProjectMember existing = testProjectMemberMapper.selectTestProjectMemberById(
                testProjectMember.getTestProjectMemberId());
        if (existing == null) {
            throw new ServiceException("成员不存在");
        }
        String newRole = testProjectMember.getMemberRole();
        if (newRole == null || newRole.isEmpty()) {
            newRole = existing.getMemberRole();
            testProjectMember.setMemberRole(newRole);
        }
        if (isOwnerRole(existing.getMemberRole()) && !isOwnerRole(newRole)) {
            throw new ServiceException(KEEP_ONE_OWNER_MSG);
        }
        Long testProjectId = existing.getTestProjectId();
        Long userId = existing.getUserId();
        testProjectMember.setTestProjectId(testProjectId);
        testProjectMember.setUserId(userId);
        newRole = TestProjectMemberRole.normalizeCode(newRole);
        testProjectMember.setMemberRole(newRole);
        transferProjectOwner(testProjectId, userId, newRole);
        testProjectMember.setUpdateTime(DateUtils.getNowDate());
        int flag = testProjectMemberMapper.updateTestProjectMember(testProjectMember);
        if (isOwnerRole(newRole)) {
            assertExactlyOneOwner(testProjectId);
        }
        // 失去 token:use 时清空 Project Token
        boolean canToken = testProjectMemberRoleService.listPermCodesByRoleKey(newRole)
                .contains(TestProjectMemberPerm.TOKEN_USE.getCode());
        if (!canToken) {
            testProjectUserSettingService.clearProjectToken(testProjectId, userId);
        }
        return flag;
    }

    /**
     * 将指定用户设为所有者：其余所有者降为管理员，并更新项目 owner_id。
     * 目标角色非所有者或参数不完整时不处理。
     *
     * @param testProjectId 项目 id
     * @param newOwnerUserId 新所有者用户 id
     * @param memberRole 待写入的成员角色
     */
    private void transferProjectOwner(Long testProjectId, Long newOwnerUserId, String memberRole) {
        if (!isOwnerRole(memberRole) || testProjectId == null || newOwnerUserId == null) {
            return;
        }
        testProjectMemberMapper.demoteOtherOwnersToAdmin(testProjectId, newOwnerUserId);
        testProjectMapper.updateOwnerId(testProjectId, newOwnerUserId);
    }

    /**
     * 校验项目下恰好有一名有效所有者
     *
     * @param testProjectId 项目 id
     */
    private void assertExactlyOneOwner(Long testProjectId) {
        int ownerCount = testProjectMemberMapper.countOwners(testProjectId);
        if (ownerCount != 1) {
            throw new ServiceException("一个项目必须恰好有一名所有者");
        }
    }

    /**
     * 禁止对所有者执行删除或降级类操作
     *
     * @param member 目标成员
     */
    private void assertNotOwner(TestProjectMember member) {
        if (member != null && isOwnerRole(member.getMemberRole())) {
            throw new ServiceException(KEEP_ONE_OWNER_MSG);
        }
    }

    /**
     * 批量删除测试项目成员
     *
     * @param testProjectMemberIdList 需要删除的测试项目成员主键集合
     * @return 结果
     */
    @Override
    public int deleteTestProjectMemberByIdList(List<Long> testProjectMemberIdList) {
        return testProjectMemberMapper.deleteTestProjectMemberByIdList(testProjectMemberIdList);
    }

    /**
     * 删除测试项目成员信息
     *
     * @param testProjectMemberId 测试项目成员主键
     * @return 结果
     */
    @Override
    public int deleteTestProjectMemberById(Long testProjectMemberId) {
        return testProjectMemberMapper.deleteTestProjectMemberById(testProjectMemberId);
    }

    /**
     * 逻辑删除测试项目成员信息
     *
     * @param testProjectMemberId 测试项目成员主键
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int logicDeleteTestProjectMemberById(Long testProjectMemberId) {
        return logicDeleteTestProjectMemberByIdList(List.of(testProjectMemberId));
    }

    /**
     * 批量逻辑删除测试项目成员信息
     *
     * @param testProjectMemberIdList 测试项目成员主键集合
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int logicDeleteTestProjectMemberByIdList(List<Long> testProjectMemberIdList) {
        if (testProjectMemberIdList == null || testProjectMemberIdList.isEmpty()) {
            return 0;
        }
        List<TestProjectMember> memberList = this.selectTestProjectMemberList(TestProjectMember.builder()
                .testProjectMemberIdList(testProjectMemberIdList)
                .build());
        for (TestProjectMember row : memberList) {
            assertNotOwner(row);
        }
        int memberFlag = testProjectMemberMapper.logicDeleteTestProjectMemberByIdList(testProjectMemberIdList);
        if (memberFlag <= 0) {
            throw new ServiceException("删除成员失败");
        }
        for (TestProjectMember row : memberList) {
            int settingFlag = testProjectUserSettingService.logicDeleteTestProjectUserSettingByProjectIdAndUserId(
                    row.getTestProjectId(), row.getUserId());
            if (settingFlag <= 0) {
                throw new ServiceException("删除用户设置失败");
            }
        }
        return memberFlag;
    }

    /**
     * 查询测试项目成员数量
     *
     * @param params 测试项目成员Params
     * @return 数量
     */
    @Override
    public int selectTestProjectMemberCount(TestProjectMemberParams params) {
        return testProjectMemberMapper.selectTestProjectMemberCount(params);
    }

    /**
     * 按条件查询单条测试项目成员
     *
     * @param params 测试项目成员Params
     * @return 测试项目成员
     */
    @Override
    public TestProjectMember selectTestProjectMemberOne(TestProjectMemberParams params) {
        return testProjectMemberMapper.selectTestProjectMemberOne(params);
    }

    /**
     * 指定项目下有效成员的用户 ID
     *
     * @param testProjectId 测试项目 ID
     * @return userId 列表，不会为 null
     */
    @Override
    public List<Long> listMemberUserIdsByTestProjectId(Long testProjectId) {
        return testProjectMemberMapper.selectMemberUserIdsByTestProjectId(testProjectId);
    }

    /**
     * 按当前登录用户解析并校验其在项目中的成员角色标识。
     *
     * @param testProjectId 项目 id
     * @return 角色 role_key
     */
    @Override
    public String getCheckProjectMemberRole(Long testProjectId) {
        return getCheckProjectMemberRole(testProjectId, SecurityUtils.getUserId());
    }

    /**
     * 按指定用户解析并校验其在项目中的成员角色标识。
     *
     * @param testProjectId 项目 id
     * @param userId 操作者用户 id
     * @return 角色 role_key
     */
    @Override
    public String getCheckProjectMemberRole(Long testProjectId, Long userId) {
        if (testProjectId == null) {
            throw new ServiceException("请指定测试项目");
        }
        if (userId == null) {
            throw new ServiceException("缺少审计用户/操作者");
        }
        if (SecurityUtils.isAdmin(userId)) {
            return TestProjectMemberRole.SYS_ADMIN.getCode();
        }
        TestProjectMember self = testProjectMemberMapper.selectTestProjectMemberOne(TestProjectMemberParams.builder()
                .testProjectId(testProjectId)
                .userId(userId)
                .build());
        if (self == null) {
            throw new ServiceException("您不是该项目成员，无权限访问");
        }
        return TestProjectMemberRole.normalizeCode(self.getMemberRole());
    }

    /**
     * 校验当前登录用户在项目下具备指定权限，不满足则抛业务异常
     *
     * @param testProjectId 项目 id
     * @param perm 权限
     */
    @Override
    public void requireCapability(Long testProjectId, TestProjectMemberPerm perm) {
        requireCapability(testProjectId, SecurityUtils.getUserId(), perm);
    }

    /**
     * 校验指定用户在项目下具备指定权限，不满足则抛业务异常
     *
     * @param testProjectId 项目 id
     * @param userId 用户 id
     * @param perm 权限
     */
    @Override
    public void requireCapability(Long testProjectId, Long userId, TestProjectMemberPerm perm) {
        if (perm == null) {
            throw new ServiceException("权限不能为空");
        }
        if (!hasCapability(testProjectId, userId, perm)) {
            throw new ServiceException("当前角色无权限：" + perm.getName());
        }
    }

    /**
     * 返回当前登录用户在项目下的权限 code 集合。
     *
     * @param testProjectId 项目 id
     * @return 权限 code 集合
     */
    @Override
    public Set<String> listCapabilities(Long testProjectId) {
        String roleKey = getCheckProjectMemberRole(testProjectId);
        return testProjectMemberRoleService.listPermCodesByRoleKey(roleKey);
    }

    /**
     * 判断指定用户是否在项目下具备某权限。
     *
     * @param testProjectId 项目 id
     * @param userId 用户 id
     * @param perm 权限
     * @return 角色权限集合含该 code 时为 true
     */
    @Override
    public boolean hasCapability(Long testProjectId, Long userId, TestProjectMemberPerm perm) {
        String roleKey = getCheckProjectMemberRole(testProjectId, userId);
        return testProjectMemberRoleService.listPermCodesByRoleKey(roleKey).contains(perm.getCode());
    }

}
