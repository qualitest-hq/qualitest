package com.qualitest.project.service;

import java.util.List;

import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.flow.model.GraphFlowOutput;
import com.qualitest.flow.model.GraphJson;
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
     * 修改测试流。
     * 写图时须带基准图版本号；服务端占写锁并做条件更新，版本不匹配抛出版本冲突。
     *
     * @param testFlow 测试流
     * @return 结果
     */
    int updateTestFlow(TestFlow testFlow);

    /**
     * 在端锁与行锁内把规范化 patch 增量应用到最新图并写库。
     * 不使用版本号 CAS；写完后 graph_revision +1。
     *
     * @param testFlowId   测试流 id
     * @param testProjectId 项目 id（可空，用于校验归属）
     * @param patch        已规范化的增量 patch
     * @param endpointId   稳定端标识；空则按短抢锁处理
     * @return 写库后的图、新版本号与警告
     */
    ApplyGraphPatchResult applyGraphPatch(Long testFlowId, Long testProjectId,
                                          FlowDesignPatch patch, String endpointId);

    /**
     * 整表替换图元数据中的对外产出声明，不改节点、边与运行场景。
     * 持端写锁并在行锁内重读图后写回，图版本号加一。
     *
     * @param testFlowId    测试流 id
     * @param testProjectId 项目 id（可空，用于校验归属）
     * @param flowOutputs   新的对外产出声明；null 视为空列表
     * @param endpointId    稳定端标识；空则按短抢锁处理
     * @return 写库后的图、新版本号与警告
     */
    ApplyGraphPatchResult replaceFlowOutputs(Long testFlowId, Long testProjectId,
                                             List<GraphFlowOutput> flowOutputs,
                                             String endpointId);

    /**
     * 图增量写库结果。
     *
     * @param graph    写库后的图
     * @param revision 写库后的图版本号
     * @param warnings 软校验警告
     */
    record ApplyGraphPatchResult(GraphJson graph, long revision, List<String> warnings) {
    }

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
