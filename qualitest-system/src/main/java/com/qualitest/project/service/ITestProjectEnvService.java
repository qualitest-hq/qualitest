package com.qualitest.project.service;

import java.util.List;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.params.TestProjectEnvParams;
import com.qualitest.project.result.TestProjectEnvResult;

/**
 * 测试项目环境Service接口
 * 
 * @author qualitest
 * @date 2026-02-05
 */
public interface ITestProjectEnvService {
    /**
     * 查询测试项目环境列表
     *
     * @param testProjectEnv 测试项目环境
     * @return 测试项目环境集合
     */
    List<TestProjectEnv> selectTestProjectEnvList(TestProjectEnv testProjectEnv);

    /**
     * 查询测试项目环境
     *
     * @param testProjectEnvId 测试项目环境主键
     * @return 测试项目环境
     */
    TestProjectEnv selectTestProjectEnvById(Long testProjectEnvId);

    /**
     * 查询测试项目环境Result列表
     *
     * @param params 测试项目环境Params
     * @return 测试项目环境Result集合
     */
    List<TestProjectEnvResult> selectTestProjectEnvResultList(TestProjectEnvParams params);

    /**
     * 获取测试项目环境详细信息
     *
     * @param testProjectEnvId 测试项目环境主键
     * @return 测试项目环境Result
     */
    TestProjectEnvResult selectTestProjectEnvResult(Long testProjectEnvId);

    /**
     * 新增测试项目环境
     * 
     * @param testProjectEnv 测试项目环境
     * @return 结果
     */
    int insertTestProjectEnv(TestProjectEnv testProjectEnv);

    /**
     * 修改测试项目环境
     * 
     * @param testProjectEnv 测试项目环境
     * @return 结果
     */
    int updateTestProjectEnv(TestProjectEnv testProjectEnv);

    /**
     * 拖动排序：按环境 ID 顺序重写 sort_num（0..n-1），单事务、一条批量 SQL。
     *
     * @param testProjectId 测试项目 ID
     * @param orderedEnvIds 从前到后的环境 ID 列表
     * @param userId        当前用户（仅允许调整本人环境）
     * @return 更新行数
     */
    int reorderTestProjectEnvs(Long testProjectId, List<Long> orderedEnvIds, Long userId);

    /**
     * 批量删除测试项目环境
     * 
     * @param testProjectEnvIdList 需要删除的测试项目环境主键集合
     * @return 结果
     */
    int deleteTestProjectEnvByIdList(List<Long> testProjectEnvIdList);

    /**
     * 删除测试项目环境信息
     * 
     * @param testProjectEnvId 测试项目环境主键
     * @return 结果
     */
    public int deleteTestProjectEnvById(Long testProjectEnvId);

    /**
     * 修改测试项目环境为逻辑删除
     *
     * @param testProjectEnvId 测试项目环境ID
     * @return 结果
     */
    int logicDeleteTestProjectEnvById(Long testProjectEnvId);

    /**
     * 批量修改测试项目环境为逻辑删除
     *
     * @param testProjectEnvIdList 测试项目环境ID集合
     * @return 结果
     */
    int logicDeleteTestProjectEnvByIdList(List<Long> testProjectEnvIdList);

    /**
     * 查询测试项目环境数量
     *
     * @param params 测试项目环境Params
     * @return 数量
     */
    int selectTestProjectEnvCount(TestProjectEnvParams params);

    /**
     * 按条件查询单条测试项目环境
     *
     * @param params 测试项目环境Params
     * @return 测试项目环境
     */
    TestProjectEnv selectTestProjectEnvOne(TestProjectEnvParams params);
}
