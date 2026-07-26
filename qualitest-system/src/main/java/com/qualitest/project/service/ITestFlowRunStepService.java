package com.qualitest.project.service;

import java.util.List;
import com.qualitest.project.domain.TestFlowRunStep;
import com.qualitest.project.params.TestFlowRunStepParams;
import com.qualitest.project.result.TestFlowRunStepResult;

/**
 * 测试流运行步骤Service接口
 * 
 * @author qualitest
 * @date 2026-06-05
 */
public interface ITestFlowRunStepService {
    /**
     * 查询测试流运行步骤列表
     *
     * @param testFlowRunStep 测试流运行步骤
     * @return 测试流运行步骤集合
     */
    List<TestFlowRunStep> selectTestFlowRunStepList(TestFlowRunStep testFlowRunStep);

    /**
     * 查询测试流运行步骤
     *
     * @param testFlowRunStepId 测试流运行步骤主键
     * @return 测试流运行步骤
     */
    TestFlowRunStep selectTestFlowRunStepById(Long testFlowRunStepId);

    /**
     * 查询测试流运行步骤Result列表
     *
     * @param params 测试流运行步骤Params
     * @return 测试流运行步骤Result集合
     */
    List<TestFlowRunStepResult> selectTestFlowRunStepResultList(TestFlowRunStepParams params);

    /**
     * 获取测试流运行步骤详细信息
     *
     * @param testFlowRunStepId 测试流运行步骤主键
     * @return 测试流运行步骤Result
     */
    TestFlowRunStepResult selectTestFlowRunStepResult(Long testFlowRunStepId);

    /**
     * 新增测试流运行步骤
     * 
     * @param testFlowRunStep 测试流运行步骤
     * @return 结果
     */
    int insertTestFlowRunStep(TestFlowRunStep testFlowRunStep);

    /**
     * 修改测试流运行步骤
     * 
     * @param testFlowRunStep 测试流运行步骤
     * @return 结果
     */
    int updateTestFlowRunStep(TestFlowRunStep testFlowRunStep);

    /**
     * 批量删除测试流运行步骤
     * 
     * @param testFlowRunStepIdList 需要删除的测试流运行步骤主键集合
     * @return 结果
     */
    int deleteTestFlowRunStepByIdList(List<Long> testFlowRunStepIdList);

    /**
     * 删除测试流运行步骤信息
     * 
     * @param testFlowRunStepId 测试流运行步骤主键
     * @return 结果
     */
    public int deleteTestFlowRunStepById(Long testFlowRunStepId);

    /**
     * 修改测试流运行步骤为逻辑删除
     *
     * @param testFlowRunStepId 测试流运行步骤ID
     * @return 结果
     */
    int logicDeleteTestFlowRunStepById(Long testFlowRunStepId);

    /**
     * 批量修改测试流运行步骤为逻辑删除
     *
     * @param testFlowRunStepIdList 测试流运行步骤ID集合
     * @return 结果
     */
    int logicDeleteTestFlowRunStepByIdList(List<Long> testFlowRunStepIdList);

    /**
     * 查询测试流运行步骤数量
     *
     * @param params 测试流运行步骤Params
     * @return 数量
     */
    int selectTestFlowRunStepCount(TestFlowRunStepParams params);

    /**
     * 按条件查询单条测试流运行步骤
     *
     * @param params 测试流运行步骤Params
     * @return 测试流运行步骤
     */
    TestFlowRunStep selectTestFlowRunStepOne(TestFlowRunStepParams params);
}
