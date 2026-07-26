package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.utils.SecurityUtils;
import java.util.List;
import java.util.Objects;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.qualitest.project.mapper.TestFlowRunStepMapper;
import com.qualitest.project.domain.TestFlowRunStep;
import com.qualitest.project.params.TestFlowRunStepParams;
import com.qualitest.project.result.TestFlowRunStepResult;
import com.qualitest.project.service.ITestFlowRunStepService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 测试流运行步骤Service业务层处理
 * 
 * @author qualitest
 * @date 2026-06-05
 */
@Service
public class TestFlowRunStepServiceImpl implements ITestFlowRunStepService {
    @Autowired
    private TestFlowRunStepMapper testFlowRunStepMapper;

    /**
     * 查询测试流运行步骤列表
     *
     * @param testFlowRunStep 测试流运行步骤
     * @return 测试流运行步骤
     */
    @Override
    public List<TestFlowRunStep> selectTestFlowRunStepList(TestFlowRunStep testFlowRunStep) {
        return testFlowRunStepMapper.selectTestFlowRunStepList(testFlowRunStep);
    }

    /**
     * 查询测试流运行步骤
     *
     * @param testFlowRunStepId 测试流运行步骤主键
     * @return 测试流运行步骤
     */
    @Override
    public TestFlowRunStep selectTestFlowRunStepById(Long testFlowRunStepId) {
        return testFlowRunStepMapper.selectTestFlowRunStepById(testFlowRunStepId);
    }

    /**
     * 查询测试流运行步骤Result列表
     *
     * @param params 测试流运行步骤Params
     * @return 测试流运行步骤Result集合
     */
    @Override
    public List<TestFlowRunStepResult> selectTestFlowRunStepResultList(TestFlowRunStepParams params) {
        return testFlowRunStepMapper.selectTestFlowRunStepResultList(params);
    }

    /**
     * 获取测试流运行步骤详细信息
     *
     * @param testFlowRunStepId 测试流运行步骤主键
     * @return 测试流运行步骤Result
     */
    @Override
    public TestFlowRunStepResult selectTestFlowRunStepResult(Long testFlowRunStepId) {
        return testFlowRunStepMapper.selectTestFlowRunStepResult(testFlowRunStepId);
    }

    /**
     * 新增测试流运行步骤
     *
     * @param testFlowRunStep 测试流运行步骤
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestFlowRunStep(TestFlowRunStep testFlowRunStep) {
        if (Objects.isNull(testFlowRunStep.getTestFlowRunStepId())) {
            testFlowRunStep.setTestFlowRunStepId(IdUtil.getSnowflakeNextId());
        }
        testFlowRunStep.setCreateTime(DateUtils.getNowDate());
        return testFlowRunStepMapper.insertTestFlowRunStep(testFlowRunStep);
    }

    /**
     * 修改测试流运行步骤
     *
     * @param testFlowRunStep 测试流运行步骤
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestFlowRunStep(TestFlowRunStep testFlowRunStep) {
        testFlowRunStep.setUpdateTime(DateUtils.getNowDate());
        return testFlowRunStepMapper.updateTestFlowRunStep(testFlowRunStep);
    }

    /**
     * 批量删除测试流运行步骤
     * 
     * @param testFlowRunStepIdList 需要删除的测试流运行步骤主键集合
     * @return 结果
     */
    @Override
    public int deleteTestFlowRunStepByIdList(List<Long> testFlowRunStepIdList) {
        return testFlowRunStepMapper.deleteTestFlowRunStepByIdList(testFlowRunStepIdList);
    }

    /**
     * 删除测试流运行步骤信息
     * 
     * @param testFlowRunStepId 测试流运行步骤主键
     * @return 结果
     */
    @Override
    public int deleteTestFlowRunStepById(Long testFlowRunStepId) {
        return testFlowRunStepMapper.deleteTestFlowRunStepById(testFlowRunStepId);
    }

    /**
     * 逻辑删除测试流运行步骤信息
     * 
     * @param testFlowRunStepId 测试流运行步骤主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestFlowRunStepById(Long testFlowRunStepId) {
        return testFlowRunStepMapper.logicDeleteTestFlowRunStepById(testFlowRunStepId);
    }

    /**
     * 批量逻辑删除测试流运行步骤信息
     * 
     * @param testFlowRunStepIdList 测试流运行步骤主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestFlowRunStepByIdList(List<Long> testFlowRunStepIdList) {
        return testFlowRunStepMapper.logicDeleteTestFlowRunStepByIdList(testFlowRunStepIdList);
    }

    /**
     * 查询测试流运行步骤数量
     *
     * @param params 测试流运行步骤Params
     * @return 数量
     */
    @Override
    public int selectTestFlowRunStepCount(TestFlowRunStepParams params) {
        return testFlowRunStepMapper.selectTestFlowRunStepCount(params);
    }

    /**
     * 按条件查询单条测试流运行步骤
     *
     * @param params 测试流运行步骤Params
     * @return 测试流运行步骤
     */
    @Override
    public TestFlowRunStep selectTestFlowRunStepOne(TestFlowRunStepParams params) {
        return testFlowRunStepMapper.selectTestFlowRunStepOne(params);
    }
}
