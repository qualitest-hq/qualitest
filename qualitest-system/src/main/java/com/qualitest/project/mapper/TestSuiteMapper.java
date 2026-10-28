package com.qualitest.project.mapper;

import com.qualitest.project.domain.TestSuite;
import com.qualitest.project.params.TestSuiteParams;
import com.qualitest.project.result.TestSuiteResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 测试套件Mapper接口
 *
 * @author qualitest
 * @date 2026-10-28
 */
@Mapper
public interface TestSuiteMapper {

    /**
     * 查询测试套件列表
     *
     * @param testSuite 测试套件
     * @return 测试套件集合
     */
    List<TestSuite> selectTestSuiteList(TestSuite testSuite);

    /**
     * 查询测试套件
     *
     * @param testSuiteId 测试套件主键
     * @return 测试套件
     */
    TestSuite selectTestSuiteById(Long testSuiteId);

    /**
     * 查询测试套件Result列表
     *
     * @param params 测试套件Params
     * @return 测试套件Result集合
     */
    List<TestSuiteResult> selectTestSuiteResultList(TestSuiteParams params);

    /**
     * 获取测试套件详细信息
     *
     * @param testSuiteId 测试套件主键
     * @return 测试套件Result
     */
    TestSuiteResult selectTestSuiteResult(Long testSuiteId);

    /**
     * 查询测试套件数量
     *
     * @param params 测试套件Params
     * @return 数量
     */
    int selectTestSuiteCount(TestSuiteParams params);

    /**
     * 按条件查询单条测试套件
     *
     * @param params 测试套件Params
     * @return 测试套件
     */
    TestSuite selectTestSuiteOne(TestSuiteParams params);

    /**
     * 新增测试套件
     *
     * @param testSuite 测试套件
     * @return 结果
     */
    int insertTestSuite(TestSuite testSuite);

    /**
     * 修改测试套件
     *
     * @param testSuite 测试套件
     * @return 结果
     */
    int updateTestSuite(TestSuite testSuite);

    /**
     * 删除测试套件
     *
     * @param testSuiteId 测试套件主键
     * @return 结果
     */
    int deleteTestSuiteById(Long testSuiteId);

    /**
     * 批量删除测试套件
     *
     * @param testSuiteIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestSuiteByIdList(@Param("list") List<Long> testSuiteIdList);

    /**
     * 逻辑删除测试套件
     *
     * @param testSuiteId 测试套件主键
     * @return 结果
     */
    int logicDeleteTestSuiteById(Long testSuiteId);

    /**
     * 批量逻辑删除测试套件
     *
     * @param testSuiteIdList 测试套件主键集合
     * @return 结果
     */
    int logicDeleteTestSuiteByIdList(@Param("list") List<Long> testSuiteIdList);
}
