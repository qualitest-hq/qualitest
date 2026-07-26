package com.qualitest.project.service;

import java.util.List;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.params.TestProjectApiParams;
import com.qualitest.project.result.TestProjectApiResult;

/**
 * 测试项目APIService接口
 * 
 * @author qualitest
 * @date 2026-02-05
 */
public interface ITestProjectApiService {
    /**
     * 查询测试项目API列表
     *
     * @param testProjectApi 测试项目API
     * @return 测试项目API集合
     */
    List<TestProjectApi> selectTestProjectApiList(TestProjectApi testProjectApi);

    /**
     * 查询测试项目API
     *
     * @param testProjectApiId 测试项目API主键
     * @return 测试项目API
     */
    TestProjectApi selectTestProjectApiById(Long testProjectApiId);

    /**
     * 查询测试项目APIResult列表
     *
     * @param params 测试项目APIParams
     * @return 测试项目APIResult集合
     */
    List<TestProjectApiResult> selectTestProjectApiResultList(TestProjectApiParams params);

    /**
     * 获取测试项目API详细信息
     *
     * @param testProjectApiId 测试项目API主键
     * @return 测试项目APIResult
     */
    TestProjectApiResult selectTestProjectApiResult(Long testProjectApiId);

    /**
     * 新增测试项目API
     * 
     * @param testProjectApi 测试项目API
     * @return 结果
     */
    int insertTestProjectApi(TestProjectApi testProjectApi);

    /**
     * 批量新增测试项目 API，供插件全量导入等场景使用。
     * 不在此方法内刷新 api_count，调用方在整批导入结束后统一回写。
     */
    int batchInsertTestProjectApi(List<TestProjectApi> testProjectApiList);

    /**
     * 修改测试项目API
     * 
     * @param testProjectApi 测试项目API
     * @return 结果
     */
    int updateTestProjectApi(TestProjectApi testProjectApi);

    /**
     * 批量删除测试项目API
     * 
     * @param testProjectApiIdList 需要删除的测试项目API主键集合
     * @return 结果
     */
    int deleteTestProjectApiByIdList(List<Long> testProjectApiIdList);

    /**
     * 删除测试项目API信息
     * 
     * @param testProjectApiId 测试项目API主键
     * @return 结果
     */
    public int deleteTestProjectApiById(Long testProjectApiId);

    /**
     * 修改测试项目API为逻辑删除
     *
     * @param testProjectApiId 测试项目APIID
     * @return 结果
     */
    int logicDeleteTestProjectApiById(Long testProjectApiId);

    /**
     * 批量修改测试项目API为逻辑删除
     *
     * @param testProjectApiIdList 测试项目APIID集合
     * @return 结果
     */
    int logicDeleteTestProjectApiByIdList(List<Long> testProjectApiIdList);

    /**
     * 查询测试项目API数量
     *
     * @param params 测试项目APIParams
     * @return 数量
     */
    int selectTestProjectApiCount(TestProjectApiParams params);

    /**
     * 按条件查询单条测试项目API
     *
     * @param params 测试项目APIParams
     * @return 测试项目API
     */
    TestProjectApi selectTestProjectApiOne(TestProjectApiParams params);
}
