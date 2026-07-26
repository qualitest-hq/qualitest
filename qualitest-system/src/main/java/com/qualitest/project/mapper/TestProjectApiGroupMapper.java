package com.qualitest.project.mapper;

import java.util.List;
import com.qualitest.project.domain.TestProjectApiGroup;
import com.qualitest.project.params.TestProjectApiGroupParams;
import com.qualitest.project.result.TestProjectApiGroupResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 测试项目API分组Mapper接口
 * 
 * @author qualitest
 * @date 2026-02-05
 */
@Mapper
public interface TestProjectApiGroupMapper {
    /**
     * 查询测试项目API分组列表
     *
     * @param testProjectApiGroup 测试项目API分组
     * @return 测试项目API分组集合
     */
    List<TestProjectApiGroup> selectTestProjectApiGroupList(TestProjectApiGroup testProjectApiGroup);

    /**
     * 查询测试项目API分组
     *
     * @param apiGroupId 测试项目API分组主键
     * @return 测试项目API分组
     */
    TestProjectApiGroup selectTestProjectApiGroupById(Long apiGroupId);

    /**
     * 查询测试项目API分组Result列表
     *
     * @param params 测试项目API分组Params
     * @return 测试项目API分组Result集合
     */
    List<TestProjectApiGroupResult> selectTestProjectApiGroupResultList(TestProjectApiGroupParams params);

    /**
     * 获取测试项目API分组详细信息
     *
     * @param apiGroupId 测试项目API分组主键
     * @return 测试项目API分组Result
     */
    TestProjectApiGroupResult selectTestProjectApiGroupResult(Long apiGroupId);

    /**
     * 查询测试项目API分组数量
     *
     * @param params 测试项目API分组Params
     * @return 数量
     */
    int selectTestProjectApiGroupCount(TestProjectApiGroupParams params);

    /**
     * 按条件查询单条测试项目API分组
     *
     * @param params 测试项目API分组Params
     * @return 测试项目API分组
     */
    TestProjectApiGroup selectTestProjectApiGroupOne(TestProjectApiGroupParams params);

    /**
     * 新增测试项目API分组
     * 
     * @param testProjectApiGroup 测试项目API分组
     * @return 结果
     */
    int insertTestProjectApiGroup(TestProjectApiGroup testProjectApiGroup);

    /**
     * 修改测试项目API分组
     * 
     * @param testProjectApiGroup 测试项目API分组
     * @return 结果
     */
    int updateTestProjectApiGroup(TestProjectApiGroup testProjectApiGroup);

    /**
     * 删除测试项目API分组
     * 
     * @param apiGroupId 测试项目API分组主键
     * @return 结果
     */
    int deleteTestProjectApiGroupById(Long apiGroupId);

    /**
     * 批量删除测试项目API分组
     * 
     * @param apiGroupIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestProjectApiGroupByIdList(@Param("list") List<Long> apiGroupIdList);

    /**
     * 逻辑删除测试项目API分组
     * 
     * @param apiGroupId 测试项目API分组主键
     * @return 结果
     */
    int logicDeleteTestProjectApiGroupById(Long apiGroupId);

    /**
     * 批量逻辑删除测试项目API分组
     * 
     * @param apiGroupIdList 测试项目API分组主键集合
     * @return 结果
     */
    int logicDeleteTestProjectApiGroupByIdList(@Param("list") List<Long> apiGroupIdList);
}
