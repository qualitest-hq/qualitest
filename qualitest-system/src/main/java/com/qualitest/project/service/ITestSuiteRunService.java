package com.qualitest.project.service;

import com.qualitest.project.domain.TestSuiteRun;
import com.qualitest.project.params.BindTestSuiteRunItemParams;
import com.qualitest.project.params.FinishTestSuiteRunItemParams;
import com.qualitest.project.params.StartTestSuiteRunParams;
import com.qualitest.project.params.TestSuiteRunParams;
import com.qualitest.project.result.TestSuiteRunResult;

import java.util.List;

/**
 * 测试套件运行Service接口
 *
 * @author qualitest
 * @date 2026-10-28
 */
public interface ITestSuiteRunService {

    /**
     * 查询测试套件运行列表
     *
     * @param testSuiteRun 测试套件运行
     * @return 测试套件运行集合
     */
    List<TestSuiteRun> selectTestSuiteRunList(TestSuiteRun testSuiteRun);

    /**
     * 查询测试套件运行
     *
     * @param testSuiteRunId 测试套件运行主键
     * @return 测试套件运行
     */
    TestSuiteRun selectTestSuiteRunById(Long testSuiteRunId);

    /**
     * 查询测试套件运行Result列表
     *
     * @param params 测试套件运行Params
     * @return 测试套件运行Result集合
     */
    List<TestSuiteRunResult> selectTestSuiteRunResultList(TestSuiteRunParams params);

    /**
     * 获取测试套件运行详细信息（含项）
     *
     * @param testSuiteRunId 测试套件运行主键
     * @return 测试套件运行Result
     */
    TestSuiteRunResult selectTestSuiteRunResult(Long testSuiteRunId);

    /**
     * 新增测试套件运行
     *
     * @param testSuiteRun 测试套件运行
     * @return 结果
     */
    int insertTestSuiteRun(TestSuiteRun testSuiteRun);

    /**
     * 修改测试套件运行
     *
     * @param testSuiteRun 测试套件运行
     * @return 结果
     */
    int updateTestSuiteRun(TestSuiteRun testSuiteRun);

    /**
     * 批量删除测试套件运行
     *
     * @param testSuiteRunIdList 需要删除的测试套件运行主键集合
     * @return 结果
     */
    int deleteTestSuiteRunByIdList(List<Long> testSuiteRunIdList);

    /**
     * 删除测试套件运行信息
     *
     * @param testSuiteRunId 测试套件运行主键
     * @return 结果
     */
    int deleteTestSuiteRunById(Long testSuiteRunId);

    /**
     * 修改测试套件运行为逻辑删除
     *
     * @param testSuiteRunId 测试套件运行ID
     * @return 结果
     */
    int logicDeleteTestSuiteRunById(Long testSuiteRunId);

    /**
     * 批量修改测试套件运行为逻辑删除
     *
     * @param testSuiteRunIdList 测试套件运行ID集合
     * @return 结果
     */
    int logicDeleteTestSuiteRunByIdList(List<Long> testSuiteRunIdList);

    /**
     * 查询测试套件运行数量
     *
     * @param params 测试套件运行Params
     * @return 数量
     */
    int selectTestSuiteRunCount(TestSuiteRunParams params);

    /**
     * 按条件查询单条测试套件运行
     *
     * @param params 测试套件运行Params
     * @return 测试套件运行
     */
    TestSuiteRun selectTestSuiteRunOne(TestSuiteRunParams params);

    /**
     * 开始套件运行：按已保存套件写入 running 头与 pending 项，立刻返回；由客户端串行 trigger 并回写
     *
     * @param params 启动参数（须含 testSuiteId）
     * @return 套件运行详情
     */
    TestSuiteRunResult start(StartTestSuiteRunParams params);

    /**
     * 运行项绑定正式 RunId，状态置为 running
     *
     * @param testSuiteRunId     套件运行ID
     * @param testSuiteRunItemId 套件运行项ID
     * @param params             绑定参数
     */
    void bindRun(Long testSuiteRunId, Long testSuiteRunItemId, BindTestSuiteRunItemParams params);

    /**
     * 运行项写入终态并刷新汇总
     *
     * @param testSuiteRunId     套件运行ID
     * @param testSuiteRunItemId 套件运行项ID
     * @param params             终态参数
     * @return 刷新后的套件运行详情
     */
    TestSuiteRunResult finishItem(Long testSuiteRunId, Long testSuiteRunItemId,
                                  FinishTestSuiteRunItemParams params);

    /**
     * 中止队列：未跑项标 skipped，头状态 aborted
     *
     * @param testSuiteRunId 套件运行ID
     * @return 刷新后的套件运行详情
     */
    TestSuiteRunResult abort(Long testSuiteRunId);
}
