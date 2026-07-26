package com.qualitest.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.project.domain.TestFlowRunStep;
import com.qualitest.project.params.TestFlowRunStepParams;
import com.qualitest.project.result.TestFlowRunStepResult;

/**
 * 测试流运行步骤Mapper接口
 * 
 * @author qualitest
 * @date 2026-06-05
 */
@Mapper
public interface TestFlowRunStepMapper {
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
     * 删除测试流运行步骤
     * 
     * @param testFlowRunStepId 测试流运行步骤主键
     * @return 结果
     */
    int deleteTestFlowRunStepById(Long testFlowRunStepId);

    /**
     * 批量删除测试流运行步骤
     * 
     * @param testFlowRunStepIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestFlowRunStepByIdList(@Param("list") List<Long> testFlowRunStepIdList);

    /**
     * 逻辑删除测试流运行步骤
     * 
     * @param testFlowRunStepId 测试流运行步骤主键
     * @return 结果
     */
    int logicDeleteTestFlowRunStepById(Long testFlowRunStepId);

    /**
     * 批量逻辑删除测试流运行步骤
     * 
     * @param testFlowRunStepIdList 测试流运行步骤主键集合
     * @return 结果
     */
    int logicDeleteTestFlowRunStepByIdList(@Param("list") List<Long> testFlowRunStepIdList);
}
