package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.common.utils.SecurityUtils;
import com.qualitest.common.utils.uuid.IdUtils;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.domain.TestProjectMember;
import com.qualitest.project.domain.TestProjectUserSetting;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.mapper.TestProjectUserSettingMapper;
import com.qualitest.project.params.TestProjectMemberParams;
import com.qualitest.project.params.TestProjectUserSettingParams;
import com.qualitest.project.result.TestProjectUserSettingResult;
import com.qualitest.project.service.ITestProjectEnvService;
import com.qualitest.project.service.ITestProjectMemberService;
import com.qualitest.project.service.ITestProjectUserSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 测试项目用户设置Service业务层处理
 *
 * @author qualitest
 * @date 2026-02-09
 */
@Service
public class TestProjectUserSettingServiceImpl implements ITestProjectUserSettingService {
    @Autowired
    private TestProjectUserSettingMapper testProjectUserSettingMapper;

    @Autowired
    @Lazy
    private ITestProjectMemberService testProjectMemberService;

    @Autowired
    private ITestProjectEnvService testProjectEnvService;

    /**
     * 查询测试项目用户设置列表
     *
     * @param testProjectUserSetting 测试项目用户设置
     * @return 测试项目用户设置
     */
    @Override
    public List<TestProjectUserSetting> selectTestProjectUserSettingList(TestProjectUserSetting testProjectUserSetting) {
        return testProjectUserSettingMapper.selectTestProjectUserSettingList(testProjectUserSetting);
    }

    /**
     * 查询测试项目用户设置
     *
     * @param testProjectUserSettingId 测试项目用户设置主键
     * @return 测试项目用户设置
     */
    @Override
    public TestProjectUserSetting selectTestProjectUserSettingById(Long testProjectUserSettingId) {
        return testProjectUserSettingMapper.selectTestProjectUserSettingById(testProjectUserSettingId);
    }

    /**
     * 按条件查询单条测试项目用户设置 Result
     *
     * @param params 测试项目用户设置 Params
     * @return 测试项目用户设置 Result
     */
    @Override
    public TestProjectUserSettingResult selectTestProjectUserSettingResultOne(TestProjectUserSettingParams params) {
        return testProjectUserSettingMapper.selectTestProjectUserSettingResultOne(params);
    }

    /**
     * 获取测试项目用户设置详细信息
     *
     * @param testProjectUserSettingId 测试项目用户设置主键
     * @return 测试项目用户设置Result
     */
    @Override
    public TestProjectUserSettingResult selectTestProjectUserSettingResult(Long testProjectUserSettingId) {
        return testProjectUserSettingMapper.selectTestProjectUserSettingResult(testProjectUserSettingId);
    }

    /**
     * 新增测试项目用户设置
     *
     * @param testProjectUserSetting 测试项目用户设置
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestProjectUserSetting(TestProjectUserSetting testProjectUserSetting) {
        if (Objects.isNull(testProjectUserSetting.getTestProjectUserSettingId())) {
            testProjectUserSetting.setTestProjectUserSettingId(IdUtil.getSnowflakeNextId());
        }
        testProjectUserSetting.setCreateTime(DateUtils.getNowDate());
        return testProjectUserSettingMapper.insertTestProjectUserSetting(testProjectUserSetting);
    }

    /**
     * 修改测试项目用户设置
     *
     * @param testProjectUserSetting 测试项目用户设置
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestProjectUserSetting(TestProjectUserSetting testProjectUserSetting) {
        testProjectUserSetting.setUpdateTime(DateUtils.getNowDate());
        return testProjectUserSettingMapper.updateTestProjectUserSetting(testProjectUserSetting);
    }

    /**
     * 批量删除测试项目用户设置
     *
     * @param testProjectUserSettingIdList 需要删除的测试项目用户设置主键集合
     * @return 结果
     */
    @Override
    public int deleteTestProjectUserSettingByIdList(List<Long> testProjectUserSettingIdList) {
        return testProjectUserSettingMapper.deleteTestProjectUserSettingByIdList(testProjectUserSettingIdList);
    }

    /**
     * 删除测试项目用户设置信息
     *
     * @param testProjectUserSettingId 测试项目用户设置主键
     * @return 结果
     */
    @Override
    public int deleteTestProjectUserSettingById(Long testProjectUserSettingId) {
        return testProjectUserSettingMapper.deleteTestProjectUserSettingById(testProjectUserSettingId);
    }

    /**
     * 逻辑删除测试项目用户设置信息
     *
     * @param testProjectUserSettingId 测试项目用户设置主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestProjectUserSettingById(Long testProjectUserSettingId) {
        return testProjectUserSettingMapper.logicDeleteTestProjectUserSettingById(testProjectUserSettingId);
    }

    /**
     * 批量逻辑删除测试项目用户设置信息
     *
     * @param testProjectUserSettingIdList 测试项目用户设置主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestProjectUserSettingByIdList(List<Long> testProjectUserSettingIdList) {
        return testProjectUserSettingMapper.logicDeleteTestProjectUserSettingByIdList(testProjectUserSettingIdList);
    }

    /**
     * 按项目与用户逻辑删除测试项目用户设置
     *
     * @param testProjectId 测试项目ID
     * @param userId        用户ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int logicDeleteTestProjectUserSettingByProjectIdAndUserId(Long testProjectId, Long userId) {
        return testProjectUserSettingMapper.logicDeleteTestProjectUserSettingByProjectIdAndUserId(testProjectId, userId);
    }

    /**
     * 查询测试项目用户设置数量
     *
     * @param params 测试项目用户设置Params
     * @return 数量
     */
    @Override
    public int selectTestProjectUserSettingCount(TestProjectUserSettingParams params) {
        return testProjectUserSettingMapper.selectTestProjectUserSettingCount(params);
    }

    /**
     * 按条件查询单条测试项目用户设置
     *
     * @param params 测试项目用户设置Params
     * @return 测试项目用户设置
     */
    @Override
    public TestProjectUserSetting selectTestProjectUserSettingOne(TestProjectUserSettingParams params) {
        return testProjectUserSettingMapper.selectTestProjectUserSettingOne(params);
    }

    /**
     * 获取用户项目设置
     *
     * @param testProjectId 测试项目 ID
     * @param userId        用户 ID
     * @return 测试项目用户设置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestProjectUserSetting getUserSettingForProjectSetting(Long testProjectId, Long userId) {
        if (testProjectId == null || userId == null) {
            throw new ServiceException("项目ID或用户ID不能为空");
        }
        TestProjectUserSetting oldSetting = testProjectUserSettingMapper.selectTestProjectUserSettingOne(TestProjectUserSettingParams.builder()
                .testProjectId(testProjectId)
                .userId(userId)
                .build());
        if (oldSetting != null) {
            return oldSetting;
        }
        TestProjectMember member = testProjectMemberService.selectTestProjectMemberOne(TestProjectMemberParams.builder()
                .testProjectId(testProjectId)
                .userId(userId)
                .build());
        if (member == null && !SecurityUtils.isAdmin(userId)) {
            throw new ServiceException("非项目成员，无法初始化用户设置");
        }
        boolean allowProjectToken = member != null
                ? TestProjectMemberRole.canUseProjectToken(member.getMemberRole())
                : SecurityUtils.isAdmin(userId);
        TestProjectUserSetting setting = TestProjectUserSetting.builder()
                .testProjectId(testProjectId)
                .userId(userId)
                .testProjectEnvId(requireFirstEnvIdForUser(testProjectId, userId))
                .projectToken(allowProjectToken ? generateProjectToken(testProjectId, userId) : null)
                .build();
        this.insertTestProjectUserSetting(setting);
        return this.selectTestProjectUserSettingById(setting.getTestProjectUserSettingId());
    }

    /**
     * 成员入会时已创建默认环境；此处不再插入环境。若无任何环境（含超级管理员未自建），则不能补用户设置。
     */
    private Long requireFirstEnvIdForUser(Long testProjectId, Long userId) {
        List<TestProjectEnv> envs = testProjectEnvService.selectTestProjectEnvList(
                TestProjectEnv.builder().testProjectId(testProjectId).userId(userId).delStatus(0).build());
        if (envs.isEmpty()) {
            throw new ServiceException("请先在「环境管理」中创建测试环境");
        }
        return envs.get(0).getTestProjectEnvId();
    }

    /**
     * 生成项目Token
     *
     * @param testProjectId 测试项目ID
     * @param userId        用户ID
     * @return 生成的Token
     */
    @Override
    public String generateProjectToken(Long testProjectId, Long userId) {
        // 生成规则：项目ID + 用户ID + UUID + 时间戳，SHA-256 哈希
        String rawToken = testProjectId + ":" + userId + ":" +
                IdUtils.fastSimpleUUID() + ":" + System.currentTimeMillis();
        // SHA-256 哈希，取前64位
        String projectToken = DigestUtil.sha256Hex(rawToken);
        // 确保长度为64
        if (projectToken.length() > 64) {
            projectToken = projectToken.substring(0, 64);
        }
        return projectToken;
    }

    /**
     * 验证项目Token
     *
     * @param projectToken 项目Token
     * @return 测试项目用户设置
     */
    @Override
    public TestProjectUserSetting validateProjectToken(String projectToken) {
        if (StrUtil.isBlank(projectToken)) {
            throw new ServiceException("Token 不能为空");
        }
        // 通过唯一索引快速查询
        TestProjectUserSetting setting = testProjectUserSettingMapper.selectByProjectToken(projectToken);
        if (setting == null) {
            throw new ServiceException("Token 不存在");
        }
        return setting;
    }

    /**
     * 刷新项目Token
     *
     * @param testProjectUserSettingId 测试项目用户设置ID
     * @return 新的Token
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refreshProjectToken(Long testProjectUserSettingId) {
        TestProjectUserSetting setting = testProjectUserSettingMapper.selectTestProjectUserSettingById(testProjectUserSettingId);
        if (setting == null) {
            throw new ServiceException("项目设置不存在");
        }
        if (Objects.isNull(setting.getTestProjectId()) || Objects.isNull(setting.getUserId())) {
            throw new ServiceException("项目ID或用户ID不能为空");
        }
        // 验证用户权限
        Long currentUserId = SecurityUtils.getUserId();
        if (!currentUserId.equals(setting.getUserId())) {
            throw new ServiceException("无权刷新该Token");
        }
        // 生成新的Token
        String newToken = generateProjectToken(setting.getTestProjectId(), setting.getUserId());
        // 更新Token
        setting.setProjectToken(newToken);
        setting.setUpdateTime(DateUtils.getNowDate());
        testProjectUserSettingMapper.updateTestProjectUserSetting(setting);
        return newToken;
    }

}
