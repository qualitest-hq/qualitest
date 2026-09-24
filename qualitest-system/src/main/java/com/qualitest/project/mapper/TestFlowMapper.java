package com.qualitest.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.params.TestFlowParams;
import com.qualitest.project.result.TestFlowResult;

/**
 * 测试流Mapper接口
 * 
 * @author qualitest
 * @date 2026-06-05
 */
@Mapper
public interface TestFlowMapper {
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
     * 清空指定测试流的所属目录（flow_group_id 置 NULL）。
     *
     * @param testFlowId 测试流主键
     * @return 影响行数
     */
    int clearFlowGroupId(@Param("testFlowId") Long testFlowId);

    /**
     * 仅更新 API 语义健康三字段：
     * api_health_warning_count、api_health_checked_at、api_health_warning_codes。
     * 不改动 graph_json 及其他业务列。
     *
     * @param testFlow 须带 testFlowId 与上述三字段
     * @return 影响行数
     */
    int updateApiHealthFields(TestFlow testFlow);

    /**
     * 删除测试流
     * 
     * @param testFlowId 测试流主键
     * @return 结果
     */
    int deleteTestFlowById(Long testFlowId);

    /**
     * 批量删除测试流
     * 
     * @param testFlowIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestFlowByIdList(@Param("list") List<Long> testFlowIdList);

    /**
     * 逻辑删除测试流
     * 
     * @param testFlowId 测试流主键
     * @return 结果
     */
    int logicDeleteTestFlowById(Long testFlowId);

    /**
     * 批量逻辑删除测试流
     * 
     * @param testFlowIdList 测试流主键集合
     * @return 结果
     */
    int logicDeleteTestFlowByIdList(@Param("list") List<Long> testFlowIdList);
}
