package com.qualitest.project.service;

import java.util.List;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.params.TestProjectParams;
import com.qualitest.project.result.TestProjectResult;

/**
 * 测试项目Service接口
 * 
 * @author qualitest
 * @date 2026-02-05
 */
public interface ITestProjectService {
    /**
     * 查询测试项目列表
     *
     * @param testProject 测试项目
     * @return 测试项目集合
     */
    List<TestProject> selectTestProjectList(TestProject testProject);

    /**
     * 查询测试项目
     *
     * @param testProjectId 测试项目主键
     * @return 测试项目
     */
    TestProject selectTestProjectById(Long testProjectId);

    /**
     * 查询测试项目Result列表
     *
     * @param params 测试项目Params
     * @return 测试项目Result集合
     */
    List<TestProjectResult> selectTestProjectResultList(TestProjectParams params);

    /**
     * 获取测试项目详细信息
     *
     * @param testProjectId 测试项目主键
     * @return 测试项目Result
     */
    TestProjectResult selectTestProjectResult(Long testProjectId);

    /**
     * 新增测试项目
     * 
     * @param testProject 测试项目
     * @return 结果
     */
    int insertTestProject(TestProject testProject);

    /**
     * 修改测试项目
     * 
     * @param testProject 测试项目
     * @return 结果
     */
    int updateTestProject(TestProject testProject);

    /**
     * 批量删除测试项目
     * 
     * @param testProjectIdList 需要删除的测试项目主键集合
     * @return 结果
     */
    int deleteTestProjectByIdList(List<Long> testProjectIdList);

    /**
     * 删除测试项目信息
     * 
     * @param testProjectId 测试项目主键
     * @return 结果
     */
    public int deleteTestProjectById(Long testProjectId);

    /**
     * 修改测试项目为逻辑删除
     *
     * @param testProjectId 测试项目ID
     * @return 结果
     */
    int logicDeleteTestProjectById(Long testProjectId);

    /**
     * 批量修改测试项目为逻辑删除
     *
     * @param testProjectIdList 测试项目ID集合
     * @return 结果
     */
    int logicDeleteTestProjectByIdList(List<Long> testProjectIdList);

    /**
     * 查询测试项目数量
     *
     * @param params 测试项目Params
     * @return 数量
     */
    int selectTestProjectCount(TestProjectParams params);

    /**
     * 按条件查询单条测试项目
     *
     * @param params 测试项目Params
     * @return 测试项目
     */
    TestProject selectTestProjectOne(TestProjectParams params);

    /**
     * 按 test_project_api 未删除条数回写 test_project.api_count。
     * 在 API 导入结束、单条新增/删除 API 等场景调用。
     */
    int refreshApiCount(Long testProjectId);
}
