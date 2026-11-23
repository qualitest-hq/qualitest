package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.utils.SecurityUtils;
import java.util.List;
import java.util.Objects;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.qualitest.project.mapper.TestPluginPackageMapper;
import com.qualitest.project.domain.TestPluginPackage;
import com.qualitest.project.params.TestPluginPackageParams;
import com.qualitest.project.result.TestPluginPackageResult;
import com.qualitest.project.service.ITestPluginPackageService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台插件包登记Service业务层处理
 * 
 * @author qualitest
 * @date 2026-11-16
 */
@Service
public class TestPluginPackageServiceImpl implements ITestPluginPackageService {
    @Autowired
    private TestPluginPackageMapper testPluginPackageMapper;

    /**
     * 查询平台插件包登记列表
     *
     * @param testPluginPackage 平台插件包登记
     * @return 平台插件包登记
     */
    @Override
    public List<TestPluginPackage> selectTestPluginPackageList(TestPluginPackage testPluginPackage) {
        return testPluginPackageMapper.selectTestPluginPackageList(testPluginPackage);
    }

    /**
     * 查询平台插件包登记
     *
     * @param testPluginPackageId 平台插件包登记主键
     * @return 平台插件包登记
     */
    @Override
    public TestPluginPackage selectTestPluginPackageById(Long testPluginPackageId) {
        return testPluginPackageMapper.selectTestPluginPackageById(testPluginPackageId);
    }

    /**
     * 查询平台插件包登记Result列表
     *
     * @param params 平台插件包登记Params
     * @return 平台插件包登记Result集合
     */
    @Override
    public List<TestPluginPackageResult> selectTestPluginPackageResultList(TestPluginPackageParams params) {
        return testPluginPackageMapper.selectTestPluginPackageResultList(params);
    }

    /**
     * 获取平台插件包登记详细信息
     *
     * @param testPluginPackageId 平台插件包登记主键
     * @return 平台插件包登记Result
     */
    @Override
    public TestPluginPackageResult selectTestPluginPackageResult(Long testPluginPackageId) {
        return testPluginPackageMapper.selectTestPluginPackageResult(testPluginPackageId);
    }

    /**
     * 新增平台插件包登记
     *
     * @param testPluginPackage 平台插件包登记
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestPluginPackage(TestPluginPackage testPluginPackage) {
        if (Objects.isNull(testPluginPackage.getTestPluginPackageId())) {
            testPluginPackage.setTestPluginPackageId(IdUtil.getSnowflakeNextId());
        }
        testPluginPackage.setCreateTime(DateUtils.getNowDate());
        return testPluginPackageMapper.insertTestPluginPackage(testPluginPackage);
    }

    /**
     * 修改平台插件包登记
     *
     * @param testPluginPackage 平台插件包登记
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestPluginPackage(TestPluginPackage testPluginPackage) {
        testPluginPackage.setUpdateTime(DateUtils.getNowDate());
        return testPluginPackageMapper.updateTestPluginPackage(testPluginPackage);
    }

    /**
     * 批量删除平台插件包登记
     * 
     * @param testPluginPackageIdList 需要删除的平台插件包登记主键集合
     * @return 结果
     */
    @Override
    public int deleteTestPluginPackageByIdList(List<Long> testPluginPackageIdList) {
        return testPluginPackageMapper.deleteTestPluginPackageByIdList(testPluginPackageIdList);
    }

    /**
     * 删除平台插件包登记信息
     * 
     * @param testPluginPackageId 平台插件包登记主键
     * @return 结果
     */
    @Override
    public int deleteTestPluginPackageById(Long testPluginPackageId) {
        return testPluginPackageMapper.deleteTestPluginPackageById(testPluginPackageId);
    }

    /**
     * 逻辑删除平台插件包登记信息
     * 
     * @param testPluginPackageId 平台插件包登记主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestPluginPackageById(Long testPluginPackageId) {
        return testPluginPackageMapper.logicDeleteTestPluginPackageById(testPluginPackageId);
    }

    /**
     * 批量逻辑删除平台插件包登记信息
     * 
     * @param testPluginPackageIdList 平台插件包登记主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestPluginPackageByIdList(List<Long> testPluginPackageIdList) {
        return testPluginPackageMapper.logicDeleteTestPluginPackageByIdList(testPluginPackageIdList);
    }

    /**
     * 查询平台插件包登记数量
     *
     * @param params 平台插件包登记Params
     * @return 数量
     */
    @Override
    public int selectTestPluginPackageCount(TestPluginPackageParams params) {
        return testPluginPackageMapper.selectTestPluginPackageCount(params);
    }

    /**
     * 按条件查询单条平台插件包登记
     *
     * @param params 平台插件包登记Params
     * @return 平台插件包登记
     */
    @Override
    public TestPluginPackage selectTestPluginPackageOne(TestPluginPackageParams params) {
        return testPluginPackageMapper.selectTestPluginPackageOne(params);
    }
}
