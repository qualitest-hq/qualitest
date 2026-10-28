package com.qualitest.project.mapper;

import com.qualitest.project.domain.TestSuiteRunItem;
import com.qualitest.project.params.TestSuiteRunItemParams;
import com.qualitest.project.result.TestSuiteRunItemResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 测试套件运行项Mapper接口
 *
 * @author qualitest
 * @date 2026-10-28
 */
@Mapper
public interface TestSuiteRunItemMapper {

    /**
     * 查询测试套件运行项列表
     *
     * @param testSuiteRunItem 测试套件运行项
     * @return 测试套件运行项集合
     */
    List<TestSuiteRunItem> selectTestSuiteRunItemList(TestSuiteRunItem testSuiteRunItem);

    /**
     * 查询测试套件运行项
     *
     * @param testSuiteRunItemId 测试套件运行项主键
     * @return 测试套件运行项
     */
    TestSuiteRunItem selectTestSuiteRunItemById(Long testSuiteRunItemId);

    /**
     * 查询测试套件运行项Result列表
     *
     * @param params 测试套件运行项Params
     * @return 测试套件运行项Result集合
     */
    List<TestSuiteRunItemResult> selectTestSuiteRunItemResultList(TestSuiteRunItemParams params);

    /**
     * 获取测试套件运行项详细信息
     *
     * @param testSuiteRunItemId 测试套件运行项主键
     * @return 测试套件运行项Result
     */
    TestSuiteRunItemResult selectTestSuiteRunItemResult(Long testSuiteRunItemId);

    /**
     * 查询测试套件运行项数量
     *
     * @param params 测试套件运行项Params
     * @return 数量
     */
    int selectTestSuiteRunItemCount(TestSuiteRunItemParams params);

    /**
     * 按条件查询单条测试套件运行项
     *
     * @param params 测试套件运行项Params
     * @return 测试套件运行项
     */
    TestSuiteRunItem selectTestSuiteRunItemOne(TestSuiteRunItemParams params);

    /**
     * 新增测试套件运行项
     *
     * @param testSuiteRunItem 测试套件运行项
     * @return 结果
     */
    int insertTestSuiteRunItem(TestSuiteRunItem testSuiteRunItem);

    /**
     * 修改测试套件运行项
     *
     * @param testSuiteRunItem 测试套件运行项
     * @return 结果
     */
    int updateTestSuiteRunItem(TestSuiteRunItem testSuiteRunItem);

    /**
     * 删除测试套件运行项
     *
     * @param testSuiteRunItemId 测试套件运行项主键
     * @return 结果
     */
    int deleteTestSuiteRunItemById(Long testSuiteRunItemId);

    /**
     * 批量删除测试套件运行项
     *
     * @param testSuiteRunItemIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestSuiteRunItemByIdList(@Param("list") List<Long> testSuiteRunItemIdList);

    /**
     * 逻辑删除测试套件运行项
     *
     * @param testSuiteRunItemId 测试套件运行项主键
     * @return 结果
     */
    int logicDeleteTestSuiteRunItemById(Long testSuiteRunItemId);

    /**
     * 批量逻辑删除测试套件运行项
     *
     * @param testSuiteRunItemIdList 测试套件运行项主键集合
     * @return 结果
     */
    int logicDeleteTestSuiteRunItemByIdList(@Param("list") List<Long> testSuiteRunItemIdList);

    /**
     * 将 pending 项标记为 skipped
     *
     * @param testSuiteRunId 套件运行ID
     * @param errorMessage   错误信息
     * @return 结果
     */
    int markPendingSkipped(@Param("testSuiteRunId") Long testSuiteRunId,
                           @Param("errorMessage") String errorMessage);
}
