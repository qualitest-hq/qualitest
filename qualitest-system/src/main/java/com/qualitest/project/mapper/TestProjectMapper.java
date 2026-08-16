package com.qualitest.project.mapper;

import java.util.List;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.params.TestProjectParams;
import com.qualitest.project.result.TestProjectResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 测试项目Mapper接口
 * 
 * @author qualitest
 * @date 2026-02-05
 */
@Mapper
public interface TestProjectMapper {
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
     * 删除测试项目
     * 
     * @param testProjectId 测试项目主键
     * @return 结果
     */
    int deleteTestProjectById(Long testProjectId);

    /**
     * 批量删除测试项目
     * 
     * @param testProjectIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestProjectByIdList(@Param("list") List<Long> testProjectIdList);

    /**
     * 逻辑删除测试项目
     * 
     * @param testProjectId 测试项目主键
     * @return 结果
     */
    int logicDeleteTestProjectById(Long testProjectId);

    /**
     * 批量逻辑删除测试项目
     * 
     * @param testProjectIdList 测试项目主键集合
     * @return 结果
     */
    int logicDeleteTestProjectByIdList(@Param("list") List<Long> testProjectIdList);

    /**
     * 查询项目素材库 JSON 数组列
     *
     * @param testProjectId 测试项目主键
     * @return asset_variables 文本
     */
    String selectAssetVariablesByTestProjectId(Long testProjectId);

    /**
     * 仅更新素材库列
     *
     * @param testProject 须含 testProjectId、assetVariables
     * @return 结果
     */
    int updateAssetVariables(TestProject testProject);

    /**
     * 子查询统计未删除 API 条数，更新 test_project.api_count 与 update_time。
     */
    int refreshApiCount(Long testProjectId);

    /**
     * 仅更新项目所有者
     */
    int updateOwnerId(@Param("testProjectId") Long testProjectId, @Param("ownerId") Long ownerId);

}
