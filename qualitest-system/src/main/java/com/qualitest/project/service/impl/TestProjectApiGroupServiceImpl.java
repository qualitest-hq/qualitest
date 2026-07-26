package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import com.qualitest.common.utils.DateUtils;
import com.qualitest.project.domain.TestProjectApiGroup;
import com.qualitest.project.mapper.TestProjectApiGroupMapper;
import com.qualitest.project.params.TestProjectApiGroupParams;
import com.qualitest.project.result.TestProjectApiGroupResult;
import com.qualitest.project.service.ITestProjectApiGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 测试项目API分组Service业务层处理
 *
 * @author qualitest
 * @date 2026-02-05
 */
@Service
public class TestProjectApiGroupServiceImpl implements ITestProjectApiGroupService {
    @Autowired
    private TestProjectApiGroupMapper testProjectApiGroupMapper;

    /**
     * 查询测试项目API分组列表
     *
     * @param testProjectApiGroup 测试项目API分组
     * @return 测试项目API分组
     */
    @Override
    public List<TestProjectApiGroup> selectTestProjectApiGroupList(TestProjectApiGroup testProjectApiGroup) {
        return testProjectApiGroupMapper.selectTestProjectApiGroupList(testProjectApiGroup);
    }

    /**
     * 查询测试项目API分组
     *
     * @param apiGroupId 测试项目API分组主键
     * @return 测试项目API分组
     */
    @Override
    public TestProjectApiGroup selectTestProjectApiGroupById(Long apiGroupId) {
        return testProjectApiGroupMapper.selectTestProjectApiGroupById(apiGroupId);
    }

    /**
     * 查询测试项目API分组Result列表
     *
     * @param params 测试项目API分组Params
     * @return 测试项目API分组Result集合
     */
    @Override
    public List<TestProjectApiGroupResult> selectTestProjectApiGroupResultList(TestProjectApiGroupParams params) {
        return testProjectApiGroupMapper.selectTestProjectApiGroupResultList(params);
    }

    /**
     * 获取测试项目API分组详细信息
     *
     * @param apiGroupId 测试项目API分组主键
     * @return 测试项目API分组Result
     */
    @Override
    public TestProjectApiGroupResult selectTestProjectApiGroupResult(Long apiGroupId) {
        return testProjectApiGroupMapper.selectTestProjectApiGroupResult(apiGroupId);
    }

    /**
     * 新增测试项目API分组
     *
     * @param testProjectApiGroup 测试项目API分组
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestProjectApiGroup(TestProjectApiGroup testProjectApiGroup) {
        if (Objects.isNull(testProjectApiGroup.getApiGroupId())) {
            testProjectApiGroup.setApiGroupId(IdUtil.getSnowflakeNextId());
        }
        testProjectApiGroup.setCreateTime(DateUtils.getNowDate());
        return testProjectApiGroupMapper.insertTestProjectApiGroup(testProjectApiGroup);
    }

    /**
     * 修改测试项目API分组
     *
     * @param testProjectApiGroup 测试项目API分组
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestProjectApiGroup(TestProjectApiGroup testProjectApiGroup) {
        testProjectApiGroup.setUpdateTime(DateUtils.getNowDate());
        return testProjectApiGroupMapper.updateTestProjectApiGroup(testProjectApiGroup);
    }

    /**
     * 批量删除测试项目API分组
     *
     * @param apiGroupIdList 需要删除的测试项目API分组主键集合
     * @return 结果
     */
    @Override
    public int deleteTestProjectApiGroupByIdList(List<Long> apiGroupIdList) {
        return testProjectApiGroupMapper.deleteTestProjectApiGroupByIdList(apiGroupIdList);
    }

    /**
     * 删除测试项目API分组信息
     *
     * @param apiGroupId 测试项目API分组主键
     * @return 结果
     */
    @Override
    public int deleteTestProjectApiGroupById(Long apiGroupId) {
        return testProjectApiGroupMapper.deleteTestProjectApiGroupById(apiGroupId);
    }

    /**
     * 逻辑删除测试项目API分组信息
     *
     * @param apiGroupId 测试项目API分组主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestProjectApiGroupById(Long apiGroupId) {
        return testProjectApiGroupMapper.logicDeleteTestProjectApiGroupById(apiGroupId);
    }

    /**
     * 批量逻辑删除测试项目API分组信息
     *
     * @param apiGroupIdList 测试项目API分组主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestProjectApiGroupByIdList(List<Long> apiGroupIdList) {
        return testProjectApiGroupMapper.logicDeleteTestProjectApiGroupByIdList(apiGroupIdList);
    }

    /**
     * 查询测试项目API分组数量
     *
     * @param params 测试项目API分组Params
     * @return 数量
     */
    @Override
    public int selectTestProjectApiGroupCount(TestProjectApiGroupParams params) {
        return testProjectApiGroupMapper.selectTestProjectApiGroupCount(params);
    }

    /**
     * 按条件查询单条测试项目API分组
     *
     * @param params 测试项目API分组Params
     * @return 测试项目API分组
     */
    @Override
    public TestProjectApiGroup selectTestProjectApiGroupOne(TestProjectApiGroupParams params) {
        return testProjectApiGroupMapper.selectTestProjectApiGroupOne(params);
    }
}
