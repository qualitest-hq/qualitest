package com.qualitest.project.service;

import java.util.List;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.params.TestFlowRunParams;
import com.qualitest.project.result.TestFlowRunResult;

/**
 * 测试流运行Service接口
 * 
 * @author qualitest
 * @date 2026-06-05
 */
public interface ITestFlowRunService {
    /**
     * 查询测试流运行列表
     *
     * @param testFlowRun 测试流运行
     * @return 测试流运行集合
     */
    List<TestFlowRun> selectTestFlowRunList(TestFlowRun testFlowRun);

    /**
     * 查询测试流运行
     *
     * @param testFlowRunId 测试流运行主键
     * @return 测试流运行
     */
    TestFlowRun selectTestFlowRunById(Long testFlowRunId);

    /**
     * 查询测试流运行Result列表
     *
     * @param params 测试流运行Params
     * @return 测试流运行Result集合
     */
    List<TestFlowRunResult> selectTestFlowRunResultList(TestFlowRunParams params);

    /**
     * 获取测试流运行详细信息
     *
     * @param testFlowRunId 测试流运行主键
     * @return 测试流运行Result
     */
    TestFlowRunResult selectTestFlowRunResult(Long testFlowRunId);

    /**
     * 新增测试流运行
     * 
     * @param testFlowRun 测试流运行
     * @return 结果
     */
    int insertTestFlowRun(TestFlowRun testFlowRun);

    /**
     * 修改测试流运行
     * 
     * @param testFlowRun 测试流运行
     * @return 结果
     */
    int updateTestFlowRun(TestFlowRun testFlowRun);

    /**
     * 乐观锁：paused → running。
     *
     * @return 影响行数
     */
    int updateStatusFromPausedToRunning(Long testFlowRunId);

    /**
     * 批量删除测试流运行
     * 
     * @param testFlowRunIdList 需要删除的测试流运行主键集合
     * @return 结果
     */
    int deleteTestFlowRunByIdList(List<Long> testFlowRunIdList);

    /**
     * 删除测试流运行信息
     * 
     * @param testFlowRunId 测试流运行主键
     * @return 结果
     */
    public int deleteTestFlowRunById(Long testFlowRunId);

    /**
     * 修改测试流运行为逻辑删除
     *
     * @param testFlowRunId 测试流运行ID
     * @return 结果
     */
    int logicDeleteTestFlowRunById(Long testFlowRunId);

    /**
     * 批量修改测试流运行为逻辑删除
     *
     * @param testFlowRunIdList 测试流运行ID集合
     * @return 结果
     */
    int logicDeleteTestFlowRunByIdList(List<Long> testFlowRunIdList);

    /**
     * 查询测试流运行数量
     *
     * @param params 测试流运行Params
     * @return 数量
     */
    int selectTestFlowRunCount(TestFlowRunParams params);

    /**
     * 按条件查询单条测试流运行
     *
     * @param params 测试流运行Params
     * @return 测试流运行
     */
    TestFlowRun selectTestFlowRunOne(TestFlowRunParams params);
}
