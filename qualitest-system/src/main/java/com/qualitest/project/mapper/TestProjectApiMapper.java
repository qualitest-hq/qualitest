package com.qualitest.project.mapper;

import java.util.List;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.params.TestProjectApiParams;
import com.qualitest.project.result.TestProjectApiResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 测试项目APIMapper接口
 * 
 * @author qualitest
 * @date 2026-02-05
 */
@Mapper
public interface TestProjectApiMapper {
    /**
     * 查询测试项目API列表
     *
     * @param testProjectApi 测试项目API
     * @return 测试项目API集合
     */
    List<TestProjectApi> selectTestProjectApiList(TestProjectApi testProjectApi);

    /**
     * 查询测试项目API
     *
     * @param testProjectApiId 测试项目API主键
     * @return 测试项目API
     */
    TestProjectApi selectTestProjectApiById(Long testProjectApiId);

    /**
     * 查询测试项目APIResult列表
     *
     * @param params 测试项目APIParams
     * @return 测试项目APIResult集合
     */
    List<TestProjectApiResult> selectTestProjectApiResultList(TestProjectApiParams params);

    /**
     * 获取测试项目API详细信息
     *
     * @param testProjectApiId 测试项目API主键
     * @return 测试项目APIResult
     */
    TestProjectApiResult selectTestProjectApiResult(Long testProjectApiId);

    /**
     * 查询测试项目API数量
     *
     * @param params 测试项目APIParams
     * @return 数量
     */
    int selectTestProjectApiCount(TestProjectApiParams params);

    /**
     * 按条件查询单条测试项目API
     *
     * @param params 测试项目APIParams
     * @return 测试项目API
     */
    TestProjectApi selectTestProjectApiOne(TestProjectApiParams params);

    /**
     * 新增测试项目API
     * 
     * @param testProjectApi 测试项目API
     * @return 结果
     */
    int insertTestProjectApi(TestProjectApi testProjectApi);

    /**
     * 多行 VALUES 批量插入 test_project_api，由 Service 层按批次拆分后调用。
     */
    int batchInsertTestProjectApi(@Param("list") List<TestProjectApi> list);

    /**
     * 修改测试项目API
     * 
     * @param testProjectApi 测试项目API
     * @return 结果
     */
    int updateTestProjectApi(TestProjectApi testProjectApi);

    /**
     * 删除测试项目API
     * 
     * @param testProjectApiId 测试项目API主键
     * @return 结果
     */
    int deleteTestProjectApiById(Long testProjectApiId);

    /**
     * 批量删除测试项目API
     * 
     * @param testProjectApiIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestProjectApiByIdList(@Param("list") List<Long> testProjectApiIdList);

    /**
     * 逻辑删除测试项目API
     * 
     * @param testProjectApiId 测试项目API主键
     * @return 结果
     */
    int logicDeleteTestProjectApiById(Long testProjectApiId);

    /**
     * 批量逻辑删除测试项目API
     * 
     * @param testProjectApiIdList 测试项目API主键集合
     * @return 结果
     */
    int logicDeleteTestProjectApiByIdList(@Param("list") List<Long> testProjectApiIdList);

    /**
     * 按 API 主键列表查询所属项目 ID（去重），批量逻辑删除后用于刷新各项目 api_count。
     */
    List<Long> selectDistinctTestProjectIdsByApiIdList(@Param("list") List<Long> testProjectApiIdList);

    /**
     * 按关键词搜索项目 API（设计工具 search_apis 使用）。
     * 匹配 api_name、api_path、api_description，限制返回条数。
     */
    List<TestProjectApi> searchApisByKeyword(@Param("testProjectId") Long testProjectId,
                                             @Param("keyword") String keyword,
                                             @Param("limit") int limit);
}
