package com.qualitest.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.project.domain.TestPluginPackage;
import com.qualitest.project.params.TestPluginPackageParams;
import com.qualitest.project.result.TestPluginPackageResult;

/**
 * 平台插件包登记Mapper接口
 * 
 * @author qualitest
 * @date 2026-11-16
 */
@Mapper
public interface TestPluginPackageMapper {
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
     * 删除平台插件包登记
     * 
     * @param testPluginPackageId 平台插件包登记主键
     * @return 结果
     */
    int deleteTestPluginPackageById(Long testPluginPackageId);

    /**
     * 批量删除平台插件包登记
     * 
     * @param testPluginPackageIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestPluginPackageByIdList(@Param("list") List<Long> testPluginPackageIdList);

    /**
     * 逻辑删除平台插件包登记
     * 
     * @param testPluginPackageId 平台插件包登记主键
     * @return 结果
     */
    int logicDeleteTestPluginPackageById(Long testPluginPackageId);

    /**
     * 批量逻辑删除平台插件包登记
     * 
     * @param testPluginPackageIdList 平台插件包登记主键集合
     * @return 结果
     */
    int logicDeleteTestPluginPackageByIdList(@Param("list") List<Long> testPluginPackageIdList);
}
