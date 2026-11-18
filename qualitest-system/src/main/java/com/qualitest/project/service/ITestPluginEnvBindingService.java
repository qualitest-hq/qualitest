package com.qualitest.project.service;

import java.util.List;
import com.qualitest.project.domain.TestPluginEnvBinding;
import com.qualitest.project.params.TestPluginEnvBindingParams;
import com.qualitest.project.result.TestPluginEnvBindingResult;

/**
 * 测试环境插件绑定Service接口
 * 
 * @author qualitest
 * @date 2026-11-16
 */
public interface ITestPluginEnvBindingService {
    /**
     * 查询测试环境插件绑定列表
     *
     * @param testPluginEnvBinding 测试环境插件绑定
     * @return 测试环境插件绑定集合
     */
    List<TestPluginEnvBinding> selectTestPluginEnvBindingList(TestPluginEnvBinding testPluginEnvBinding);

    /**
     * 查询测试环境插件绑定
     *
     * @param testPluginEnvBindingId 测试环境插件绑定主键
     * @return 测试环境插件绑定
     */
    TestPluginEnvBinding selectTestPluginEnvBindingById(Long testPluginEnvBindingId);

    /**
     * 查询测试环境插件绑定Result列表
     *
     * @param params 测试环境插件绑定Params
     * @return 测试环境插件绑定Result集合
     */
    List<TestPluginEnvBindingResult> selectTestPluginEnvBindingResultList(TestPluginEnvBindingParams params);

    /**
     * 获取测试环境插件绑定详细信息
     *
     * @param testPluginEnvBindingId 测试环境插件绑定主键
     * @return 测试环境插件绑定Result
     */
    TestPluginEnvBindingResult selectTestPluginEnvBindingResult(Long testPluginEnvBindingId);

    /**
     * 新增测试环境插件绑定
     * 
     * @param testPluginEnvBinding 测试环境插件绑定
     * @return 结果
     */
    int insertTestPluginEnvBinding(TestPluginEnvBinding testPluginEnvBinding);

    /**
     * 修改测试环境插件绑定
     * 
     * @param testPluginEnvBinding 测试环境插件绑定
     * @return 结果
     */
    int updateTestPluginEnvBinding(TestPluginEnvBinding testPluginEnvBinding);

    /**
     * 批量删除测试环境插件绑定
     * 
     * @param testPluginEnvBindingIdList 需要删除的测试环境插件绑定主键集合
     * @return 结果
     */
    int deleteTestPluginEnvBindingByIdList(List<Long> testPluginEnvBindingIdList);

    /**
     * 删除测试环境插件绑定信息
     * 
     * @param testPluginEnvBindingId 测试环境插件绑定主键
     * @return 结果
     */
    public int deleteTestPluginEnvBindingById(Long testPluginEnvBindingId);

    /**
     * 修改测试环境插件绑定为逻辑删除
     *
     * @param testPluginEnvBindingId 测试环境插件绑定ID
     * @return 结果
     */
    int logicDeleteTestPluginEnvBindingById(Long testPluginEnvBindingId);

    /**
     * 批量修改测试环境插件绑定为逻辑删除
     *
     * @param testPluginEnvBindingIdList 测试环境插件绑定ID集合
     * @return 结果
     */
    int logicDeleteTestPluginEnvBindingByIdList(List<Long> testPluginEnvBindingIdList);

    /**
     * 查询测试环境插件绑定数量
     *
     * @param params 测试环境插件绑定Params
     * @return 数量
     */
    int selectTestPluginEnvBindingCount(TestPluginEnvBindingParams params);

    /**
     * 按条件查询单条测试环境插件绑定
     *
     * @param params 测试环境插件绑定Params
     * @return 测试环境插件绑定
     */
    TestPluginEnvBinding selectTestPluginEnvBindingOne(TestPluginEnvBindingParams params);
}
