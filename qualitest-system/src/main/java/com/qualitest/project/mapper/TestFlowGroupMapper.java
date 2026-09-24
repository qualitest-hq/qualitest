package com.qualitest.project.mapper;

import com.qualitest.project.domain.TestFlowGroup;
import com.qualitest.project.params.TestFlowGroupParams;
import com.qualitest.project.result.TestFlowGroupResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 测试流分组Mapper接口
 *
 * @author qualitest
 * @date 2026-09-24
 */
@Mapper
public interface TestFlowGroupMapper {

    /**
     * 查询测试流分组列表
     *
     * @param testFlowGroup 测试流分组
     * @return 测试流分组集合
     */
    List<TestFlowGroup> selectTestFlowGroupList(TestFlowGroup testFlowGroup);

    /**
     * 查询测试流分组
     *
     * @param flowGroupId 测试流分组主键
     * @return 测试流分组
     */
    TestFlowGroup selectTestFlowGroupById(Long flowGroupId);

    /**
     * 查询测试流分组Result列表
     *
     * @param params 测试流分组Params
     * @return 测试流分组Result集合
     */
    List<TestFlowGroupResult> selectTestFlowGroupResultList(TestFlowGroupParams params);

    /**
     * 获取测试流分组详细信息
     *
     * @param flowGroupId 测试流分组主键
     * @return 测试流分组Result
     */
    TestFlowGroupResult selectTestFlowGroupResult(Long flowGroupId);

    /**
     * 查询测试流分组数量
     *
     * @param params 测试流分组Params
     * @return 数量
     */
    int selectTestFlowGroupCount(TestFlowGroupParams params);

    /**
     * 新增测试流分组
     *
     * @param testFlowGroup 测试流分组
     * @return 结果
     */
    int insertTestFlowGroup(TestFlowGroup testFlowGroup);

    /**
     * 修改测试流分组
     *
     * @param testFlowGroup 测试流分组
     * @return 结果
     */
    int updateTestFlowGroup(TestFlowGroup testFlowGroup);

    /**
     * 逻辑删除测试流分组
     *
     * @param flowGroupId 测试流分组主键
     * @return 结果
     */
    int logicDeleteTestFlowGroupById(Long flowGroupId);

    /**
     * 批量逻辑删除测试流分组
     *
     * @param flowGroupIdList 测试流分组主键集合
     * @return 结果
     */
    int logicDeleteTestFlowGroupByIdList(@Param("list") List<Long> flowGroupIdList);

    /**
     * 将指定分组下的测试流挂组字段清空
     *
     * @param flowGroupId 测试流分组主键
     * @return 影响行数
     */
    int clearFlowGroupIdOnFlows(Long flowGroupId);

    /**
     * 查询某分组的直接子分组数量（未删除）
     *
     * @param parentId 父分组ID
     * @return 数量
     */
    int countChildrenByParentId(Long parentId);

    /**
     * 查询某分组及其子孙分组 id（未删除）
     *
     * @param flowGroupId 分组ID
     * @param testProjectId 项目ID
     * @return 分组 id 列表（含自身）
     */
    List<Long> selectSelfAndDescendantIds(@Param("flowGroupId") Long flowGroupId,
                                          @Param("testProjectId") Long testProjectId);
}
