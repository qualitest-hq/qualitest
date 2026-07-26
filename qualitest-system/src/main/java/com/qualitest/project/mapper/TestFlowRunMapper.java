package com.qualitest.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.project.domain.TestFlowRun;
import com.qualitest.project.params.TestFlowRunParams;
import com.qualitest.project.result.TestFlowRunResult;

/**
 * 测试流运行Mapper接口
 * 
 * @author qualitest
 * @date 2026-06-05
 */
@Mapper
public interface TestFlowRunMapper {
    /**
     * 查询测试流运行列表
     *
     * @param testFlowRun 测试流运行
     * @return 测试流运行集合
     */
    List<TestFlowRun> selectTestFlowRunList(TestFlowRun testFlowRun);

    /**
     * 查询测试流运行
     *
     * @param testFlowRunId 测试流运行主键
     * @return 测试流运行
     */
    TestFlowRun selectTestFlowRunById(Long testFlowRunId);

    /**
     * 查询测试流运行Result列表
     *
     * @param params 测试流运行Params
     * @return 测试流运行Result集合
     */
    List<TestFlowRunResult> selectTestFlowRunResultList(TestFlowRunParams params);

    /**
     * 获取测试流运行详细信息
     *
     * @param testFlowRunId 测试流运行主键
     * @return 测试流运行Result
     */
    TestFlowRunResult selectTestFlowRunResult(Long testFlowRunId);

    /**
     * 查询测试流运行数量
     *
     * @param params 测试流运行Params
     * @return 数量
     */
    int selectTestFlowRunCount(TestFlowRunParams params);

    /**
     * 按条件查询单条测试流运行
     *
     * @param params 测试流运行Params
     * @return 测试流运行
     */
    TestFlowRun selectTestFlowRunOne(TestFlowRunParams params);

    /**
     * 新增测试流运行
     * 
     * @param testFlowRun 测试流运行
     * @return 结果
     */
    int insertTestFlowRun(TestFlowRun testFlowRun);

    /**
     * 修改测试流运行
     * 
     * @param testFlowRun 测试流运行
     * @return 结果
     */
    int updateTestFlowRun(TestFlowRun testFlowRun);

    /**
     * 乐观锁：仅当当前 status=paused 时更新为 running。
     *
     * @return 影响行数，0 表示状态已变更（并发 resume 或已非 paused）
     */
    int updateStatusFromPausedToRunning(@Param("testFlowRunId") Long testFlowRunId,
                                        @Param("updateTime") java.util.Date updateTime);

    /**
     * 删除测试流运行
     * 
     * @param testFlowRunId 测试流运行主键
     * @return 结果
     */
    int deleteTestFlowRunById(Long testFlowRunId);

    /**
     * 批量删除测试流运行
     * 
     * @param testFlowRunIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestFlowRunByIdList(@Param("list") List<Long> testFlowRunIdList);

    /**
     * 逻辑删除测试流运行
     * 
     * @param testFlowRunId 测试流运行主键
     * @return 结果
     */
    int logicDeleteTestFlowRunById(Long testFlowRunId);

    /**
     * 批量逻辑删除测试流运行
     * 
     * @param testFlowRunIdList 测试流运行主键集合
     * @return 结果
     */
    int logicDeleteTestFlowRunByIdList(@Param("list") List<Long> testFlowRunIdList);
}
