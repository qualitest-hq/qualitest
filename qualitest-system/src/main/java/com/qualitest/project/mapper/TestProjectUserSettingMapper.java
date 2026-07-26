package com.qualitest.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.project.domain.TestProjectUserSetting;
import com.qualitest.project.params.TestProjectUserSettingParams;
import com.qualitest.project.result.TestProjectUserSettingResult;

/**
 * 测试项目用户设置Mapper接口
 * 
 * @author qualitest
 * @date 2026-02-09
 */
@Mapper
public interface TestProjectUserSettingMapper {
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
     * @return 测试项目用户设置 Result
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
     * 删除测试项目用户设置
     * 
     * @param testProjectUserSettingId 测试项目用户设置主键
     * @return 结果
     */
    int deleteTestProjectUserSettingById(Long testProjectUserSettingId);

    /**
     * 批量删除测试项目用户设置
     * 
     * @param testProjectUserSettingIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestProjectUserSettingByIdList(@Param("list") List<Long> testProjectUserSettingIdList);

    /**
     * 逻辑删除测试项目用户设置
     * 
     * @param testProjectUserSettingId 测试项目用户设置主键
     * @return 结果
     */
    int logicDeleteTestProjectUserSettingById(Long testProjectUserSettingId);

    /**
     * 批量逻辑删除测试项目用户设置
     * 
     * @param testProjectUserSettingIdList 测试项目用户设置主键集合
     * @return 结果
     */
    int logicDeleteTestProjectUserSettingByIdList(@Param("list") List<Long> testProjectUserSettingIdList);

    /**
     * 按项目与用户逻辑删除测试项目用户设置
     */
    int logicDeleteTestProjectUserSettingByProjectIdAndUserId(@Param("testProjectId") Long testProjectId,
                                                              @Param("userId") Long userId);

    /**
     * 根据 projectToken 查询测试项目用户设置
     *
     * @param projectToken 项目Token
     * @return 测试项目用户设置
     */
    TestProjectUserSetting selectByProjectToken(String projectToken);
}
