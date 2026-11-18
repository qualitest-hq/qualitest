package com.qualitest.project.service;

import java.util.List;
import com.qualitest.project.domain.TestPluginPackage;
import com.qualitest.project.params.TestPluginPackageParams;
import com.qualitest.project.result.TestPluginPackageResult;

/**
 * 平台插件包登记Service接口
 * 
 * @author qualitest
 * @date 2026-11-16
 */
public interface ITestPluginPackageService {
    /**
     * 查询平台插件包登记列表
     *
     * @param testPluginPackage 平台插件包登记
     * @return 平台插件包登记集合
     */
    List<TestPluginPackage> selectTestPluginPackageList(TestPluginPackage testPluginPackage);

    /**
     * 查询平台插件包登记
     *
     * @param testPluginPackageId 平台插件包登记主键
     * @return 平台插件包登记
     */
    TestPluginPackage selectTestPluginPackageById(Long testPluginPackageId);

    /**
     * 查询平台插件包登记Result列表
     *
     * @param params 平台插件包登记Params
     * @return 平台插件包登记Result集合
     */
    List<TestPluginPackageResult> selectTestPluginPackageResultList(TestPluginPackageParams params);

    /**
     * 获取平台插件包登记详细信息
     *
     * @param testPluginPackageId 平台插件包登记主键
     * @return 平台插件包登记Result
     */
    TestPluginPackageResult selectTestPluginPackageResult(Long testPluginPackageId);

    /**
     * 新增平台插件包登记
     * 
     * @param testPluginPackage 平台插件包登记
     * @return 结果
     */
    int insertTestPluginPackage(TestPluginPackage testPluginPackage);

    /**
     * 修改平台插件包登记
     * 
     * @param testPluginPackage 平台插件包登记
     * @return 结果
     */
    int updateTestPluginPackage(TestPluginPackage testPluginPackage);

    /**
     * 批量删除平台插件包登记
     * 
     * @param testPluginPackageIdList 需要删除的平台插件包登记主键集合
     * @return 结果
     */
    int deleteTestPluginPackageByIdList(List<Long> testPluginPackageIdList);

    /**
     * 删除平台插件包登记信息
     * 
     * @param testPluginPackageId 平台插件包登记主键
     * @return 结果
     */
    public int deleteTestPluginPackageById(Long testPluginPackageId);

    /**
     * 修改平台插件包登记为逻辑删除
     *
     * @param testPluginPackageId 平台插件包登记ID
     * @return 结果
     */
    int logicDeleteTestPluginPackageById(Long testPluginPackageId);

    /**
     * 批量修改平台插件包登记为逻辑删除
     *
     * @param testPluginPackageIdList 平台插件包登记ID集合
     * @return 结果
     */
    int logicDeleteTestPluginPackageByIdList(List<Long> testPluginPackageIdList);

    /**
     * 查询平台插件包登记数量
     *
     * @param params 平台插件包登记Params
     * @return 数量
     */
    int selectTestPluginPackageCount(TestPluginPackageParams params);

    /**
     * 按条件查询单条平台插件包登记
     *
     * @param params 平台插件包登记Params
     * @return 平台插件包登记
     */
    TestPluginPackage selectTestPluginPackageOne(TestPluginPackageParams params);
}
