package com.qualitest.project.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qualitest.common.utils.SecurityUtils;
import java.util.List;
import java.util.Objects;
import com.qualitest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.qualitest.project.mapper.TestPluginEnvBindingMapper;
import com.qualitest.project.domain.TestPluginEnvBinding;
import com.qualitest.project.params.TestPluginEnvBindingParams;
import com.qualitest.project.result.TestPluginEnvBindingResult;
import com.qualitest.project.service.ITestPluginEnvBindingService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 测试环境插件绑定Service业务层处理
 * 
 * @author qualitest
 * @date 2026-11-16
 */
@Service
public class TestPluginEnvBindingServiceImpl implements ITestPluginEnvBindingService {
    @Autowired
    private TestPluginEnvBindingMapper testPluginEnvBindingMapper;

    /**
     * 查询测试环境插件绑定列表
     *
     * @param testPluginEnvBinding 测试环境插件绑定
     * @return 测试环境插件绑定
     */
    @Override
    public List<TestPluginEnvBinding> selectTestPluginEnvBindingList(TestPluginEnvBinding testPluginEnvBinding) {
        return testPluginEnvBindingMapper.selectTestPluginEnvBindingList(testPluginEnvBinding);
    }

    /**
     * 查询测试环境插件绑定
     *
     * @param testPluginEnvBindingId 测试环境插件绑定主键
     * @return 测试环境插件绑定
     */
    @Override
    public TestPluginEnvBinding selectTestPluginEnvBindingById(Long testPluginEnvBindingId) {
        return testPluginEnvBindingMapper.selectTestPluginEnvBindingById(testPluginEnvBindingId);
    }

    /**
     * 查询测试环境插件绑定Result列表
     *
     * @param params 测试环境插件绑定Params
     * @return 测试环境插件绑定Result集合
     */
    @Override
    public List<TestPluginEnvBindingResult> selectTestPluginEnvBindingResultList(TestPluginEnvBindingParams params) {
        return testPluginEnvBindingMapper.selectTestPluginEnvBindingResultList(params);
    }

    /**
     * 获取测试环境插件绑定详细信息
     *
     * @param testPluginEnvBindingId 测试环境插件绑定主键
     * @return 测试环境插件绑定Result
     */
    @Override
    public TestPluginEnvBindingResult selectTestPluginEnvBindingResult(Long testPluginEnvBindingId) {
        return testPluginEnvBindingMapper.selectTestPluginEnvBindingResult(testPluginEnvBindingId);
    }

    /**
     * 新增测试环境插件绑定
     *
     * @param testPluginEnvBinding 测试环境插件绑定
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int insertTestPluginEnvBinding(TestPluginEnvBinding testPluginEnvBinding) {
        if (Objects.isNull(testPluginEnvBinding.getTestPluginEnvBindingId())) {
            testPluginEnvBinding.setTestPluginEnvBindingId(IdUtil.getSnowflakeNextId());
        }
        testPluginEnvBinding.setCreateTime(DateUtils.getNowDate());
        return testPluginEnvBindingMapper.insertTestPluginEnvBinding(testPluginEnvBinding);
    }

    /**
     * 修改测试环境插件绑定
     *
     * @param testPluginEnvBinding 测试环境插件绑定
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateTestPluginEnvBinding(TestPluginEnvBinding testPluginEnvBinding) {
        testPluginEnvBinding.setUpdateTime(DateUtils.getNowDate());
        return testPluginEnvBindingMapper.updateTestPluginEnvBinding(testPluginEnvBinding);
    }

    /**
     * 批量删除测试环境插件绑定
     * 
     * @param testPluginEnvBindingIdList 需要删除的测试环境插件绑定主键集合
     * @return 结果
     */
    @Override
    public int deleteTestPluginEnvBindingByIdList(List<Long> testPluginEnvBindingIdList) {
        return testPluginEnvBindingMapper.deleteTestPluginEnvBindingByIdList(testPluginEnvBindingIdList);
    }

    /**
     * 删除测试环境插件绑定信息
     * 
     * @param testPluginEnvBindingId 测试环境插件绑定主键
     * @return 结果
     */
    @Override
    public int deleteTestPluginEnvBindingById(Long testPluginEnvBindingId) {
        return testPluginEnvBindingMapper.deleteTestPluginEnvBindingById(testPluginEnvBindingId);
    }

    /**
     * 逻辑删除测试环境插件绑定信息
     * 
     * @param testPluginEnvBindingId 测试环境插件绑定主键
     * @return 结果
     */
    @Override
    public int logicDeleteTestPluginEnvBindingById(Long testPluginEnvBindingId) {
        return testPluginEnvBindingMapper.logicDeleteTestPluginEnvBindingById(testPluginEnvBindingId);
    }

    /**
     * 批量逻辑删除测试环境插件绑定信息
     * 
     * @param testPluginEnvBindingIdList 测试环境插件绑定主键集合
     * @return 结果
     */
    @Override
    public int logicDeleteTestPluginEnvBindingByIdList(List<Long> testPluginEnvBindingIdList) {
        return testPluginEnvBindingMapper.logicDeleteTestPluginEnvBindingByIdList(testPluginEnvBindingIdList);
    }

    /**
     * 查询测试环境插件绑定数量
     *
     * @param params 测试环境插件绑定Params
     * @return 数量
     */
    @Override
    public int selectTestPluginEnvBindingCount(TestPluginEnvBindingParams params) {
        return testPluginEnvBindingMapper.selectTestPluginEnvBindingCount(params);
    }

    /**
     * 按条件查询单条测试环境插件绑定
     *
     * @param params 测试环境插件绑定Params
     * @return 测试环境插件绑定
     */
    @Override
    public TestPluginEnvBinding selectTestPluginEnvBindingOne(TestPluginEnvBindingParams params) {
        return testPluginEnvBindingMapper.selectTestPluginEnvBindingOne(params);
    }
}
