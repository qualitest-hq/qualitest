package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.api.util.ProjectAuthConfigSupport;
import com.qualitest.project.constant.TestProjectConstants;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.params.TestProjectParams;
import com.qualitest.project.result.TestProjectResult;
import com.qualitest.project.service.ITestProjectService;
import com.qualitest.project.support.ResponseConventionSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 测试项目Service业务层处理
 *
 * @author qualitest
 * @date 2026-02-05
 */
@Service
public class TestProjectServiceImpl implements ITestProjectService {
    @Autowired
    private TestProjectMapper testProjectMapper;

    /**
     * 查询测试项目列表
     *
     * @param testProject 测试项目
     * @return 测试项目
     */
    @Override
    public List<TestProject> selectTestProjectList(TestProject testProject) {
        return testProjectMapper.selectTestProjectList(testProject);
    }

    /**
     * 查询测试项目
     *
     * @param testProjectId 测试项目主键
     * @return 测试项目
     */
    @Override
    public TestProject selectTestProjectById(Long testProjectId) {
        return testProjectMapper.selectTestProjectById(testProjectId);
    }

    /**
     * 查询测试项目Result列表
     *
     * @param params 测试项目Params
     * @return 测试项目Result集合
     */
    @Override
    public List<TestProjectResult> selectTestProjectResultList(TestProjectParams params) {
        return testProjectMapper.selectTestProjectResultList(params);
    }

    /**
     * 获取测试项目详细信息
     *
     * @param testProjectId 测试项目主键
     * @return 测试项目Result
     */
    @Override
    public TestProjectResult selectTestProjectResult(Long testProjectId) {
        TestProjectResult result = testProjectMapper.selectTestProjectResult(testProjectId);
        if (result != null) {
            // 有 Profile 但预制接口全空时，提示去勾选模板
            result.setNeedsAuthTemplateHint(ProjectAuthConfigSupport.needsAuthTemplateHint(
                    ProjectAuthConfigSupport.parse(result.getAuthConfig())));
        }
        return result;
    }

    /**
     * 新增测试项目
     *
     * @param testProject 测试项目
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestProject(TestProject testProject) {
        if (Objects.isNull(testProject.getTestProjectId())) {
            testProject.setTestProjectId(IdUtil.getSnowflakeNextId());
        }
        if (StrUtil.isBlank(testProject.getAssetVariables())) {
            testProject.setAssetVariables(TestProjectConstants.EMPTY_ASSET_VARIABLES_JSON);
        }
        // 新建项目写入默认响应约定；若调用方已传约定则先规范化（补全缺省字段）再落库
        if (StrUtil.isBlank(testProject.getResponseConvention())) {
            testProject.setResponseConvention(ResponseConventionSupport.DEFAULT_JSON);
        } else {
            testProject.setResponseConvention(
                    ResponseConventionSupport.normalizeToJson(testProject.getResponseConvention()));
        }
        // 勾了模板：auth_config 先空着；Apply 须在默认环境建好后由 Controller 调用（seedEnvs 依赖已有环境行）
        // 没勾模板：必须自带非空 Profile，否则拒绝新建
        List<Long> templateIds = testProject.getTemplateIds();
        boolean hasTemplates = templateIds != null && templateIds.stream().anyMatch(Objects::nonNull);
        if (hasTemplates) {
            testProject.setAuthConfig(null);
        } else if (testProject.getAuthConfig() != null) {
            String normalized = ProjectAuthConfigSupport.normalizeToJson(testProject.getAuthConfig());
            if (ProjectAuthConfigSupport.isEmpty(ProjectAuthConfigSupport.parse(normalized))) {
                throw new ServiceException("新建项目须至少勾选一套项目模板");
            }
            testProject.setAuthConfig(normalized);
        } else {
            throw new ServiceException("新建项目须至少勾选一套项目模板");
        }
        testProject.setCreateTime(DateUtils.getNowDate());
        return testProjectMapper.insertTestProject(testProject);
    }

    /**
     * 修改测试项目
     *
     * @param testProject 测试项目
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestProject(TestProject testProject) {
        // 素材库另有接口维护，此处不更新 asset_variables
        testProject.setAssetVariables(null);
        // 本次提交含 responseConvention 时：规范化缺省字段后写入；未提交则不改动该列
        if (StrUtil.isNotBlank(testProject.getResponseConvention())) {
            testProject.setResponseConvention(
                    ResponseConventionSupport.normalizeToJson(testProject.getResponseConvention()));
        }
        // 本次提交含 authConfig（含空串清空）时规范化后写入；未提交（null）则不改动该列
        if (testProject.getAuthConfig() != null) {
            testProject.setAuthConfig(ProjectAuthConfigSupport.normalizeToJson(testProject.getAuthConfig()));
        }
        testProject.setUpdateTime(DateUtils.getNowDate());
        return testProjectMapper.updateTestProject(testProject);
    }

    /**
     * 批量删除测试项目
     *
     * @param testProjectIdList 需要删除的测试项目主键集合
     * @return 结果
     */
    @Override
    public int deleteTestProjectByIdList(List<Long> testProjectIdList) {
        return testProjectMapper.deleteTestProjectByIdList(testProjectIdList);
    }

    /**
     * 删除测试项目信息
     *
     * @param testProjectId 测试项目主键
     * @return 结果
     */
    @Override
    public int deleteTestProjectById(Long testProjectId) {
        return testProjectMapper.deleteTestProjectById(testProjectId);
    }

    /**
     * 逻辑删除测试项目信息
     *
     * @param testProjectId 测试项目主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestProjectById(Long testProjectId) {
        return testProjectMapper.logicDeleteTestProjectById(testProjectId);
    }

    /**
     * 批量逻辑删除测试项目信息
     *
     * @param testProjectIdList 测试项目主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestProjectByIdList(List<Long> testProjectIdList) {
        return testProjectMapper.logicDeleteTestProjectByIdList(testProjectIdList);
    }

    /**
     * 查询测试项目数量
     *
     * @param params 测试项目Params
     * @return 数量
     */
    @Override
    public int selectTestProjectCount(TestProjectParams params) {
        return testProjectMapper.selectTestProjectCount(params);
    }

    /**
     * 按条件查询单条测试项目
     *
     * @param params 测试项目Params
     * @return 测试项目
     */
    @Override
    public TestProject selectTestProjectOne(TestProjectParams params) {
        return testProjectMapper.selectTestProjectOne(params);
    }

    /**
     * 统计 test_project_api 中未删除记录数，写入 test_project.api_count。
     */
    @Override
    public int refreshApiCount(Long testProjectId) {
        if (testProjectId == null) {
            return 0;
        }
        return testProjectMapper.refreshApiCount(testProjectId);
    }
}
