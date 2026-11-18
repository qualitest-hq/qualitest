package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.utils.SecurityUtils;
import java.util.List;
import java.util.Objects;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.qualitest.project.mapper.TestPluginProjectMountMapper;
import com.qualitest.project.domain.TestPluginProjectMount;
import com.qualitest.project.params.TestPluginProjectMountParams;
import com.qualitest.project.result.TestPluginProjectMountResult;
import com.qualitest.project.service.ITestPluginProjectMountService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 测试项目插件挂载Service业务层处理
 * 
 * @author qualitest
 * @date 2026-11-16
 */
@Service
public class TestPluginProjectMountServiceImpl implements ITestPluginProjectMountService {
    @Autowired
    private TestPluginProjectMountMapper testPluginProjectMountMapper;

    /**
     * 查询测试项目插件挂载列表
     *
     * @param testPluginProjectMount 测试项目插件挂载
     * @return 测试项目插件挂载
     */
    @Override
    public List<TestPluginProjectMount> selectTestPluginProjectMountList(TestPluginProjectMount testPluginProjectMount) {
        return testPluginProjectMountMapper.selectTestPluginProjectMountList(testPluginProjectMount);
    }

    /**
     * 查询测试项目插件挂载
     *
     * @param testPluginProjectMountId 测试项目插件挂载主键
     * @return 测试项目插件挂载
     */
    @Override
    public TestPluginProjectMount selectTestPluginProjectMountById(Long testPluginProjectMountId) {
        return testPluginProjectMountMapper.selectTestPluginProjectMountById(testPluginProjectMountId);
    }

    /**
     * 查询测试项目插件挂载Result列表
     *
     * @param params 测试项目插件挂载Params
     * @return 测试项目插件挂载Result集合
     */
    @Override
    public List<TestPluginProjectMountResult> selectTestPluginProjectMountResultList(TestPluginProjectMountParams params) {
        return testPluginProjectMountMapper.selectTestPluginProjectMountResultList(params);
    }

    /**
     * 获取测试项目插件挂载详细信息
     *
     * @param testPluginProjectMountId 测试项目插件挂载主键
     * @return 测试项目插件挂载Result
     */
    @Override
    public TestPluginProjectMountResult selectTestPluginProjectMountResult(Long testPluginProjectMountId) {
        return testPluginProjectMountMapper.selectTestPluginProjectMountResult(testPluginProjectMountId);
    }

    /**
     * 新增测试项目插件挂载
     *
     * @param testPluginProjectMount 测试项目插件挂载
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestPluginProjectMount(TestPluginProjectMount testPluginProjectMount) {
        if (Objects.isNull(testPluginProjectMount.getTestPluginProjectMountId())) {
            testPluginProjectMount.setTestPluginProjectMountId(IdUtil.getSnowflakeNextId());
        }
        testPluginProjectMount.setCreateTime(DateUtils.getNowDate());
        return testPluginProjectMountMapper.insertTestPluginProjectMount(testPluginProjectMount);
    }

    /**
     * 修改测试项目插件挂载
     *
     * @param testPluginProjectMount 测试项目插件挂载
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestPluginProjectMount(TestPluginProjectMount testPluginProjectMount) {
        testPluginProjectMount.setUpdateTime(DateUtils.getNowDate());
        return testPluginProjectMountMapper.updateTestPluginProjectMount(testPluginProjectMount);
    }

    /**
     * 批量删除测试项目插件挂载
     * 
     * @param testPluginProjectMountIdList 需要删除的测试项目插件挂载主键集合
     * @return 结果
     */
    @Override
    public int deleteTestPluginProjectMountByIdList(List<Long> testPluginProjectMountIdList) {
        return testPluginProjectMountMapper.deleteTestPluginProjectMountByIdList(testPluginProjectMountIdList);
    }

    /**
     * 删除测试项目插件挂载信息
     * 
     * @param testPluginProjectMountId 测试项目插件挂载主键
     * @return 结果
     */
    @Override
    public int deleteTestPluginProjectMountById(Long testPluginProjectMountId) {
        return testPluginProjectMountMapper.deleteTestPluginProjectMountById(testPluginProjectMountId);
    }

    /**
     * 逻辑删除测试项目插件挂载信息
     * 
     * @param testPluginProjectMountId 测试项目插件挂载主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestPluginProjectMountById(Long testPluginProjectMountId) {
        return testPluginProjectMountMapper.logicDeleteTestPluginProjectMountById(testPluginProjectMountId);
    }

    /**
     * 批量逻辑删除测试项目插件挂载信息
     * 
     * @param testPluginProjectMountIdList 测试项目插件挂载主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestPluginProjectMountByIdList(List<Long> testPluginProjectMountIdList) {
        return testPluginProjectMountMapper.logicDeleteTestPluginProjectMountByIdList(testPluginProjectMountIdList);
    }

    /**
     * 查询测试项目插件挂载数量
     *
     * @param params 测试项目插件挂载Params
     * @return 数量
     */
    @Override
    public int selectTestPluginProjectMountCount(TestPluginProjectMountParams params) {
        return testPluginProjectMountMapper.selectTestPluginProjectMountCount(params);
    }

    /**
     * 按条件查询单条测试项目插件挂载
     *
     * @param params 测试项目插件挂载Params
     * @return 测试项目插件挂载
     */
    @Override
    public TestPluginProjectMount selectTestPluginProjectMountOne(TestPluginProjectMountParams params) {
        return testPluginProjectMountMapper.selectTestPluginProjectMountOne(params);
    }
}
