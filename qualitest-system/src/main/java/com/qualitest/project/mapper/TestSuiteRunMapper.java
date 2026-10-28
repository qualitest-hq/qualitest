package com.qualitest.project.mapper;

import com.qualitest.project.domain.TestSuiteRun;
import com.qualitest.project.params.TestSuiteRunParams;
import com.qualitest.project.result.TestSuiteRunResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 测试套件运行Mapper接口
 *
 * @author qualitest
 * @date 2026-10-28
 */
@Mapper
public interface TestSuiteRunMapper {

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
     * 获取测试套件运行详细信息
     *
     * @param testSuiteRunId 测试套件运行主键
     * @return 测试套件运行Result
     */
    TestSuiteRunResult selectTestSuiteRunResult(Long testSuiteRunId);

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
     * 删除测试套件运行
     *
     * @param testSuiteRunId 测试套件运行主键
     * @return 结果
     */
    int deleteTestSuiteRunById(Long testSuiteRunId);

    /**
     * 批量删除测试套件运行
     *
     * @param testSuiteRunIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestSuiteRunByIdList(@Param("list") List<Long> testSuiteRunIdList);

    /**
     * 逻辑删除测试套件运行
     *
     * @param testSuiteRunId 测试套件运行主键
     * @return 结果
     */
    int logicDeleteTestSuiteRunById(Long testSuiteRunId);

    /**
     * 批量逻辑删除测试套件运行
     *
     * @param testSuiteRunIdList 测试套件运行主键集合
     * @return 结果
     */
    int logicDeleteTestSuiteRunByIdList(@Param("list") List<Long> testSuiteRunIdList);
}
