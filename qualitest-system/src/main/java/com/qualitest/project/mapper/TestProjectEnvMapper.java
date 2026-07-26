package com.qualitest.project.mapper;

import java.util.Date;
import java.util.List;
import com.qualitest.project.domain.TestProjectEnv;
import com.qualitest.project.params.TestProjectEnvParams;
import com.qualitest.project.result.TestProjectEnvResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 测试项目环境Mapper接口
 * 
 * @author qualitest
 * @date 2026-02-05
 */
@Mapper
public interface TestProjectEnvMapper {
    /**
     * 查询测试项目环境列表
     *
     * @param testProjectEnv 测试项目环境
     * @return 测试项目环境集合
     */
    List<TestProjectEnv> selectTestProjectEnvList(TestProjectEnv testProjectEnv);

    /**
     * 查询测试项目环境
     *
     * @param testProjectEnvId 测试项目环境主键
     * @return 测试项目环境
     */
    TestProjectEnv selectTestProjectEnvById(Long testProjectEnvId);

    /**
     * 查询测试项目环境Result列表
     *
     * @param params 测试项目环境Params
     * @return 测试项目环境Result集合
     */
    List<TestProjectEnvResult> selectTestProjectEnvResultList(TestProjectEnvParams params);

    /**
     * 获取测试项目环境详细信息
     *
     * @param testProjectEnvId 测试项目环境主键
     * @return 测试项目环境Result
     */
    TestProjectEnvResult selectTestProjectEnvResult(Long testProjectEnvId);

    /**
     * 查询测试项目环境数量
     *
     * @param params 测试项目环境Params
     * @return 数量
     */
    int selectTestProjectEnvCount(TestProjectEnvParams params);

    /**
     * 按条件查询单条测试项目环境
     *
     * @param params 测试项目环境Params
     * @return 测试项目环境
     */
    TestProjectEnv selectTestProjectEnvOne(TestProjectEnvParams params);

    /**
     * 某项目下环境的最大排序号（用于新增时追加到末尾）
     */
    Integer selectMaxSortNumByProjectId(@Param("testProjectId") Long testProjectId);

    /**
     * 某用户在某项目下的全部环境 ID（未删除），用于拖动排序校验
     */
    List<Long> selectEnvIdsByProjectAndUser(@Param("testProjectId") Long testProjectId,
                                            @Param("userId") Long userId);

    /**
     * 批量更新排序号（CASE 一条 SQL）
     */
    int batchUpdateSortNumForReorder(@Param("testProjectId") Long testProjectId,
                                     @Param("userId") Long userId,
                                     @Param("updateTime") Date updateTime,
                                     @Param("rows") List<TestProjectEnv> rows);

    /**
     * 新增测试项目环境
     * 
     * @param testProjectEnv 测试项目环境
     * @return 结果
     */
    int insertTestProjectEnv(TestProjectEnv testProjectEnv);

    /**
     * 修改测试项目环境
     * 
     * @param testProjectEnv 测试项目环境
     * @return 结果
     */
    int updateTestProjectEnv(TestProjectEnv testProjectEnv);

    /**
     * 删除测试项目环境
     * 
     * @param testProjectEnvId 测试项目环境主键
     * @return 结果
     */
    int deleteTestProjectEnvById(Long testProjectEnvId);

    /**
     * 批量删除测试项目环境
     * 
     * @param testProjectEnvIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestProjectEnvByIdList(@Param("list") List<Long> testProjectEnvIdList);

    /**
     * 逻辑删除测试项目环境
     * 
     * @param testProjectEnvId 测试项目环境主键
     * @return 结果
     */
    int logicDeleteTestProjectEnvById(Long testProjectEnvId);

    /**
     * 批量逻辑删除测试项目环境
     * 
     * @param testProjectEnvIdList 测试项目环境主键集合
     * @return 结果
     */
    int logicDeleteTestProjectEnvByIdList(@Param("list") List<Long> testProjectEnvIdList);
}
