package com.qualitest.project.service;

import com.qualitest.project.domain.TestFlowGroup;
import com.qualitest.project.params.TestFlowGroupParams;
import com.qualitest.project.result.TestFlowGroupResult;

import java.util.List;

/**
 * 测试流分组Service接口
 *
 * @author qualitest
 * @date 2026-09-24
 */
public interface ITestFlowGroupService {

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
     * 构建项目内分组树
     *
     * @param testProjectId 测试项目ID
     * @return 树根列表
     */
    List<TestFlowGroupResult> selectTestFlowGroupTree(Long testProjectId);

    /**
     * 获取测试流分组详细信息
     *
     * @param flowGroupId 测试流分组主键
     * @return 测试流分组Result
     */
    TestFlowGroupResult selectTestFlowGroupResult(Long flowGroupId);

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
     * 逻辑删除测试流分组（有子目录则拒绝；组下的流改为未分组）
     *
     * @param flowGroupIdList 测试流分组主键集合
     * @return 结果
     */
    int logicDeleteTestFlowGroupByIdList(List<Long> flowGroupIdList);

    /**
     * 查询某分组及其子孙分组 id
     *
     * @param flowGroupId   分组ID
     * @param testProjectId 项目ID
     * @return 分组 id 列表（含自身）
     */
    List<Long> selectSelfAndDescendantIds(Long flowGroupId, Long testProjectId);

    /**
     * 校验分组属于指定项目且未删除
     *
     * @param flowGroupId   分组ID
     * @param testProjectId 项目ID
     */
    void assertGroupInProject(Long flowGroupId, Long testProjectId);
}
