package com.qualitest.project.mapper;

import com.qualitest.project.domain.TestSuiteItem;
import com.qualitest.project.params.TestSuiteItemParams;
import com.qualitest.project.result.TestSuiteItemResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 测试套件项Mapper接口
 *
 * @author qualitest
 * @date 2026-10-28
 */
@Mapper
public interface TestSuiteItemMapper {

    /**
     * 查询测试套件项列表
     *
     * @param testSuiteItem 测试套件项
     * @return 测试套件项集合
     */
    List<TestSuiteItem> selectTestSuiteItemList(TestSuiteItem testSuiteItem);

    /**
     * 查询测试套件项
     *
     * @param testSuiteItemId 测试套件项主键
     * @return 测试套件项
     */
    TestSuiteItem selectTestSuiteItemById(Long testSuiteItemId);

    /**
     * 查询测试套件项Result列表
     *
     * @param params 测试套件项Params
     * @return 测试套件项Result集合
     */
    List<TestSuiteItemResult> selectTestSuiteItemResultList(TestSuiteItemParams params);

    /**
     * 获取测试套件项详细信息
     *
     * @param testSuiteItemId 测试套件项主键
     * @return 测试套件项Result
     */
    TestSuiteItemResult selectTestSuiteItemResult(Long testSuiteItemId);

    /**
     * 查询测试套件项数量
     *
     * @param params 测试套件项Params
     * @return 数量
     */
    int selectTestSuiteItemCount(TestSuiteItemParams params);

    /**
     * 按条件查询单条测试套件项
     *
     * @param params 测试套件项Params
     * @return 测试套件项
     */
    TestSuiteItem selectTestSuiteItemOne(TestSuiteItemParams params);

    /**
     * 新增测试套件项
     *
     * @param testSuiteItem 测试套件项
     * @return 结果
     */
    int insertTestSuiteItem(TestSuiteItem testSuiteItem);

    /**
     * 修改测试套件项
     *
     * @param testSuiteItem 测试套件项
     * @return 结果
     */
    int updateTestSuiteItem(TestSuiteItem testSuiteItem);

    /**
     * 删除测试套件项
     *
     * @param testSuiteItemId 测试套件项主键
     * @return 结果
     */
    int deleteTestSuiteItemById(Long testSuiteItemId);

    /**
     * 批量删除测试套件项
     *
     * @param testSuiteItemIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestSuiteItemByIdList(@Param("list") List<Long> testSuiteItemIdList);

    /**
     * 逻辑删除测试套件项
     *
     * @param testSuiteItemId 测试套件项主键
     * @return 结果
     */
    int logicDeleteTestSuiteItemById(Long testSuiteItemId);

    /**
     * 批量逻辑删除测试套件项
     *
     * @param testSuiteItemIdList 测试套件项主键集合
     * @return 结果
     */
    int logicDeleteTestSuiteItemByIdList(@Param("list") List<Long> testSuiteItemIdList);

    /**
     * 按套件ID逻辑删除全部项
     *
     * @param testSuiteId 套件ID
     * @return 结果
     */
    int logicDeleteBySuiteId(@Param("testSuiteId") Long testSuiteId);

    /**
     * 按套件ID集合逻辑删除项
     *
     * @param suiteIdList 套件ID集合
     * @return 结果
     */
    int logicDeleteBySuiteIdList(@Param("suiteIdList") List<Long> suiteIdList);
}
