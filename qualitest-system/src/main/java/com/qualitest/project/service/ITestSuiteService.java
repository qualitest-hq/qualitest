package com.qualitest.project.service;

import com.qualitest.project.domain.TestSuite;
import com.qualitest.project.params.TestSuiteParams;
import com.qualitest.project.params.TestSuiteSaveParams;
import com.qualitest.project.result.TestSuiteResult;

import java.util.List;

/**
 * 测试套件Service接口
 *
 * @author qualitest
 * @date 2026-10-28
 */
public interface ITestSuiteService {

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
     * 获取测试套件详细信息（头信息，不含项）
     *
     * @param testSuiteId 测试套件主键
     * @return 测试套件Result
     */
    TestSuiteResult selectTestSuiteResult(Long testSuiteId);

    /**
     * 获取测试套件详情（含项列表）
     *
     * @param testSuiteId 测试套件主键
     * @return 测试套件Result
     */
    TestSuiteResult selectTestSuiteDetail(Long testSuiteId);

    /**
     * 新建或更新套件（项整表替换）
     *
     * @param params 保存参数
     * @return 套件ID
     */
    Long saveTestSuite(TestSuiteSaveParams params);

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
     * 批量删除测试套件
     *
     * @param testSuiteIdList 需要删除的测试套件主键集合
     * @return 结果
     */
    int deleteTestSuiteByIdList(List<Long> testSuiteIdList);

    /**
     * 删除测试套件信息
     *
     * @param testSuiteId 测试套件主键
     * @return 结果
     */
    int deleteTestSuiteById(Long testSuiteId);

    /**
     * 修改测试套件为逻辑删除
     *
     * @param testSuiteId 测试套件ID
     * @return 结果
     */
    int logicDeleteTestSuiteById(Long testSuiteId);

    /**
     * 批量修改测试套件为逻辑删除（同时逻辑删除项）
     *
     * @param testSuiteIdList 测试套件ID集合
     * @return 结果
     */
    int logicDeleteTestSuiteByIdList(List<Long> testSuiteIdList);

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
}
