package com.qualitest.project.service;

import java.util.List;
import com.qualitest.project.domain.TestPluginProjectMount;
import com.qualitest.project.params.TestPluginProjectMountParams;
import com.qualitest.project.result.TestPluginProjectMountResult;

/**
 * 测试项目插件挂载Service接口
 * 
 * @author qualitest
 * @date 2026-11-16
 */
public interface ITestPluginProjectMountService {
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
     * 批量删除测试项目插件挂载
     * 
     * @param testPluginProjectMountIdList 需要删除的测试项目插件挂载主键集合
     * @return 结果
     */
    int deleteTestPluginProjectMountByIdList(List<Long> testPluginProjectMountIdList);

    /**
     * 删除测试项目插件挂载信息
     * 
     * @param testPluginProjectMountId 测试项目插件挂载主键
     * @return 结果
     */
    public int deleteTestPluginProjectMountById(Long testPluginProjectMountId);

    /**
     * 修改测试项目插件挂载为逻辑删除
     *
     * @param testPluginProjectMountId 测试项目插件挂载ID
     * @return 结果
     */
    int logicDeleteTestPluginProjectMountById(Long testPluginProjectMountId);

    /**
     * 批量修改测试项目插件挂载为逻辑删除
     *
     * @param testPluginProjectMountIdList 测试项目插件挂载ID集合
     * @return 结果
     */
    int logicDeleteTestPluginProjectMountByIdList(List<Long> testPluginProjectMountIdList);

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
}
