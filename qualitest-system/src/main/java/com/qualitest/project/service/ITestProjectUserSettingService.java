package com.qualitest.project.service;

import java.util.List;
import com.qualitest.project.domain.TestProjectUserSetting;
import com.qualitest.project.params.TestProjectUserSettingParams;
import com.qualitest.project.result.TestProjectUserSettingResult;

/**
 * 测试项目用户设置Service接口
 * 
 * @author qualitest
 * @date 2026-02-09
 */
public interface ITestProjectUserSettingService {
    /**
     * 查询测试项目用户设置列表
     *
     * @param testProjectUserSetting 测试项目用户设置
     * @return 测试项目用户设置集合
     */
    List<TestProjectUserSetting> selectTestProjectUserSettingList(TestProjectUserSetting testProjectUserSetting);

    /**
     * 查询测试项目用户设置
     *
     * @param testProjectUserSettingId 测试项目用户设置主键
     * @return 测试项目用户设置
     */
    TestProjectUserSetting selectTestProjectUserSettingById(Long testProjectUserSettingId);

    /**
     * 按条件查询单条测试项目用户设置 Result
     *
     * @param params 测试项目用户设置 Params
     * @return 测试项目用户设置 Result，无匹配时为 null
     */
    TestProjectUserSettingResult selectTestProjectUserSettingResultOne(TestProjectUserSettingParams params);

    /**
     * 获取测试项目用户设置详细信息
     *
     * @param testProjectUserSettingId 测试项目用户设置主键
     * @return 测试项目用户设置Result
     */
    TestProjectUserSettingResult selectTestProjectUserSettingResult(Long testProjectUserSettingId);

    /**
     * 新增测试项目用户设置
     * 
     * @param testProjectUserSetting 测试项目用户设置
     * @return 结果
     */
    int insertTestProjectUserSetting(TestProjectUserSetting testProjectUserSetting);

    /**
     * 修改测试项目用户设置
     * 
     * @param testProjectUserSetting 测试项目用户设置
     * @return 结果
     */
    int updateTestProjectUserSetting(TestProjectUserSetting testProjectUserSetting);

    /**
     * 批量删除测试项目用户设置
     * 
     * @param testProjectUserSettingIdList 需要删除的测试项目用户设置主键集合
     * @return 结果
     */
    int deleteTestProjectUserSettingByIdList(List<Long> testProjectUserSettingIdList);

    /**
     * 删除测试项目用户设置信息
     * 
     * @param testProjectUserSettingId 测试项目用户设置主键
     * @return 结果
     */
    public int deleteTestProjectUserSettingById(Long testProjectUserSettingId);

    /**
     * 修改测试项目用户设置为逻辑删除
     *
     * @param testProjectUserSettingId 测试项目用户设置ID
     * @return 结果
     */
    int logicDeleteTestProjectUserSettingById(Long testProjectUserSettingId);

    /**
     * 批量修改测试项目用户设置为逻辑删除
     *
     * @param testProjectUserSettingIdList 测试项目用户设置ID集合
     * @return 结果
     */
    int logicDeleteTestProjectUserSettingByIdList(List<Long> testProjectUserSettingIdList);

    /**
     * 按项目与用户逻辑删除测试项目用户设置
     */
    int logicDeleteTestProjectUserSettingByProjectIdAndUserId(Long testProjectId, Long userId);

    /**
     * 查询测试项目用户设置数量
     *
     * @param params 测试项目用户设置Params
     * @return 数量
     */
    int selectTestProjectUserSettingCount(TestProjectUserSettingParams params);

    /**
     * 按条件查询单条测试项目用户设置
     *
     * @param params 测试项目用户设置Params
     * @return 测试项目用户设置
     */
    TestProjectUserSetting selectTestProjectUserSettingOne(TestProjectUserSettingParams params);

    /**
     * 获取或补建当前用户在项目下的用户设置记录。
     * 补建前须在项目下已有至少一个测试环境（成员入会时一般会带好默认环境；超级管理员需自行创建）。
     *
     * @param testProjectId 测试项目 ID
     * @param userId        用户 ID
     * @return 测试项目用户设置
     */
    TestProjectUserSetting getUserSettingForProjectSetting(Long testProjectId, Long userId);

    /**
     * 生成项目Token
     *
     * @param testProjectId 测试项目ID
     * @param userId 用户ID
     * @return 生成的Token
     */
    String generateProjectToken(Long testProjectId, Long userId);

    /**
     * 验证项目Token
     *
     * @param projectToken 项目Token
     * @return 测试项目用户设置
     */
    TestProjectUserSetting validateProjectToken(String projectToken);

    /**
     * 刷新项目Token
     *
     * @param testProjectUserSettingId 测试项目用户设置ID
     * @return 新的Token
     */
    String refreshProjectToken(Long testProjectUserSettingId);

}
