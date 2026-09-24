package com.qualitest.project.service;

import java.util.List;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.params.CreateSubflowFromTemplateParams;
import com.qualitest.project.params.TestFlowParams;
import com.qualitest.project.result.TestFlowResult;

/**
 * 测试流Service接口
 * 
 * @author qualitest
 * @date 2026-06-05
 */
public interface ITestFlowService {
    /**
     * 查询测试流列表
     *
     * @param testFlow 测试流
     * @return 测试流集合
     */
    List<TestFlow> selectTestFlowList(TestFlow testFlow);

    /**
     * 查询测试流
     *
     * @param testFlowId 测试流主键
     * @return 测试流
     */
    TestFlow selectTestFlowById(Long testFlowId);

    /**
     * 查询测试流Result列表
     *
     * @param params 测试流Params
     * @return 测试流Result集合
     */
    List<TestFlowResult> selectTestFlowResultList(TestFlowParams params);

    /**
     * 获取测试流详细信息
     *
     * @param testFlowId 测试流主键
     * @return 测试流Result
     */
    TestFlowResult selectTestFlowResult(Long testFlowId);

    /**
     * 新增测试流
     * 
     * @param testFlow 测试流
     * @return 结果
     */
    int insertTestFlow(TestFlow testFlow);

    /**
     * 修改测试流
     * 
     * @param testFlow 测试流
     * @return 结果
     */
    int updateTestFlow(TestFlow testFlow);

    /**
     * 清空测试流所属目录（变为未分组）。
     *
     * @param testFlowId 测试流主键
     * @return 影响行数
     */
    int clearFlowGroupId(Long testFlowId);

    /**
     * 批量删除测试流
     * 
     * @param testFlowIdList 需要删除的测试流主键集合
     * @return 结果
     */
    int deleteTestFlowByIdList(List<Long> testFlowIdList);

    /**
     * 删除测试流信息
     * 
     * @param testFlowId 测试流主键
     * @return 结果
     */
    public int deleteTestFlowById(Long testFlowId);

    /**
     * 修改测试流为逻辑删除
     *
     * @param testFlowId 测试流ID
     * @return 结果
     */
    int logicDeleteTestFlowById(Long testFlowId);

    /**
     * 批量修改测试流为逻辑删除
     *
     * @param testFlowIdList 测试流ID集合
     * @return 结果
     */
    int logicDeleteTestFlowByIdList(List<Long> testFlowIdList);

    /**
     * 查询测试流数量
     *
     * @param params 测试流Params
     * @return 数量
     */
    int selectTestFlowCount(TestFlowParams params);

    /**
     * 按条件查询单条测试流
     *
     * @param params 测试流Params
     * @return 测试流
     */
    TestFlow selectTestFlowOne(TestFlowParams params);

    /**
     * 从平台子流模板创建项目内测试流定义。
     *
     * @return 新测试流 id
     */
    Long createFromSubflowTemplate(CreateSubflowFromTemplateParams params);
}
