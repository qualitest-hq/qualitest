package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.project.constant.TestProjectConstants;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.domain.TestProjectMember;
import com.qualitest.project.domain.TestProjectUserSetting;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.mapper.TestProjectMemberMapper;
import com.qualitest.project.params.TestProjectMemberParams;
import com.qualitest.project.result.TestProjectMemberResult;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.project.service.ITestProjectUserSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 测试项目成员Service业务层处理
 *
 * @author qualitest
 * @date 2026-02-05
 */
@Service
public class TestProjectMemberServiceImpl implements ITestProjectMemberService {

    @Autowired
    private TestProjectMemberMapper testProjectMemberMapper;

    @Autowired
    private ITestProjectUserSettingService testProjectUserSettingService;

    @Autowired
    private ITestProjectEnvService testProjectEnvService;

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
        // 检查是否已存在
        TestProjectMember oldMember = this.selectTestProjectMemberOne(TestProjectMemberParams.builder()
                .testProjectMemberId(testProjectMember.getTestProjectMemberId())
                .testProjectId(testProjectMember.getTestProjectId())
                .build());
        if (oldMember != null) {
            throw new ServiceException("该用户已是本项目成员");
        }
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
        // 生成 projectToken
        String projectToken = TestProjectMemberRole.canUseProjectToken(testProjectMember.getMemberRole())
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
        testProjectMember.setUpdateTime(DateUtils.getNowDate());
        return testProjectMemberMapper.updateTestProjectMember(testProjectMember);
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
        TestProjectMember member = testProjectMemberMapper.selectTestProjectMemberById(testProjectMemberId);
        int memberFlag = testProjectMemberMapper.logicDeleteTestProjectMemberById(testProjectMemberId);
        if (memberFlag <= 0) {
            throw new ServiceException("删除成员失败");
        }
        int settingFlag = testProjectUserSettingService.logicDeleteTestProjectUserSettingByProjectIdAndUserId(
                member.getTestProjectId(), member.getUserId());
        if (settingFlag <= 0) {
            throw new ServiceException("删除用户设置失败");
        }
        return memberFlag;
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
     * 获取并检查项目成员角色
     *
     * @param testProjectId 项目ID
     * @return 项目角色
     */
    @Override
    public TestProjectMemberRole getCheckProjectMemberRole(Long testProjectId) {
        if (testProjectId == null) {
            throw new ServiceException("请指定测试项目");
        }
        if (SecurityUtils.isAdmin()) {
            return TestProjectMemberRole.SYS_ADMIN;
        }
        TestProjectMember self = testProjectMemberMapper.selectTestProjectMemberOne(TestProjectMemberParams.builder()
                .testProjectId(testProjectId)
                .userId(SecurityUtils.getUserId())
                .build());
        if (self == null) {
            throw new ServiceException("您不是该项目成员，无权限访问");
        }
        return TestProjectMemberRole.getByCode(self.getMemberRole());
    }

}
