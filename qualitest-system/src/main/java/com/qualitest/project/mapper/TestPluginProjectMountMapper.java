package com.qualitest.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.project.domain.TestPluginProjectMount;
import com.qualitest.project.params.TestPluginProjectMountParams;
import com.qualitest.project.result.TestPluginProjectMountResult;

/**
 * 测试项目插件挂载Mapper接口
 * 
 * @author qualitest
 * @date 2026-11-16
 */
@Mapper
public interface TestPluginProjectMountMapper {
    /**
     * 查询测试项目插件挂载列表
     *
     * @param testPluginProjectMount 测试项目插件挂载
     * @return 测试项目插件挂载集合
     */
    List<TestPluginProjectMount> selectTestPluginProjectMountList(TestPluginProjectMount testPluginProjectMount);

    /**
     * 查询测试项目插件挂载
     *
     * @param testPluginProjectMountId 测试项目插件挂载主键
     * @return 测试项目插件挂载
     */
    TestPluginProjectMount selectTestPluginProjectMountById(Long testPluginProjectMountId);

    /**
     * 查询测试项目插件挂载Result列表
     *
     * @param params 测试项目插件挂载Params
     * @return 测试项目插件挂载Result集合
     */
    List<TestPluginProjectMountResult> selectTestPluginProjectMountResultList(TestPluginProjectMountParams params);

    /**
     * 获取测试项目插件挂载详细信息
     *
     * @param testPluginProjectMountId 测试项目插件挂载主键
     * @return 测试项目插件挂载Result
     */
    TestPluginProjectMountResult selectTestPluginProjectMountResult(Long testPluginProjectMountId);

    /**
     * 查询测试项目插件挂载数量
     *
     * @param params 测试项目插件挂载Params
     * @return 数量
     */
    int selectTestPluginProjectMountCount(TestPluginProjectMountParams params);

    /**
     * 按条件查询单条测试项目插件挂载
     *
     * @param params 测试项目插件挂载Params
     * @return 测试项目插件挂载
     */
    TestPluginProjectMount selectTestPluginProjectMountOne(TestPluginProjectMountParams params);

    /**
     * 新增测试项目插件挂载
     * 
     * @param testPluginProjectMount 测试项目插件挂载
     * @return 结果
     */
    int insertTestPluginProjectMount(TestPluginProjectMount testPluginProjectMount);

    /**
     * 修改测试项目插件挂载
     * 
     * @param testPluginProjectMount 测试项目插件挂载
     * @return 结果
     */
    int updateTestPluginProjectMount(TestPluginProjectMount testPluginProjectMount);

    /**
     * 删除测试项目插件挂载
     * 
     * @param testPluginProjectMountId 测试项目插件挂载主键
     * @return 结果
     */
    int deleteTestPluginProjectMountById(Long testPluginProjectMountId);

    /**
     * 批量删除测试项目插件挂载
     * 
     * @param testPluginProjectMountIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestPluginProjectMountByIdList(@Param("list") List<Long> testPluginProjectMountIdList);

    /**
     * 逻辑删除测试项目插件挂载
     * 
     * @param testPluginProjectMountId 测试项目插件挂载主键
     * @return 结果
     */
    int logicDeleteTestPluginProjectMountById(Long testPluginProjectMountId);

    /**
     * 批量逻辑删除测试项目插件挂载
     * 
     * @param testPluginProjectMountIdList 测试项目插件挂载主键集合
     * @return 结果
     */
    int logicDeleteTestPluginProjectMountByIdList(@Param("list") List<Long> testPluginProjectMountIdList);
}
