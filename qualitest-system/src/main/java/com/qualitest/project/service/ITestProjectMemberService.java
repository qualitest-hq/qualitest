package com.qualitest.project.service;

import com.qualitest.project.domain.TestProjectMember;
import com.qualitest.project.enums.TestProjectMemberRole;
import com.qualitest.project.params.TestProjectMemberParams;
import com.qualitest.project.result.TestProjectMemberResult;

import java.util.List;

/**
 * 测试项目成员Service接口
 *
 * @author qualitest
 * @date 2026-02-05
 */
public interface ITestProjectMemberService {

    /**
     * 查询测试项目成员列表
     *
     * @param testProjectMember 测试项目成员
     * @return 测试项目成员集合
     */
    List<TestProjectMember> selectTestProjectMemberList(TestProjectMember testProjectMember);

    /**
     * 查询测试项目成员
     *
     * @param testProjectMemberId 测试项目成员主键
     * @return 测试项目成员
     */
    TestProjectMember selectTestProjectMemberById(Long testProjectMemberId);

    /**
     * 查询测试项目成员Result列表
     *
     * @param params 测试项目成员Params
     * @return 测试项目成员Result集合
     */
    List<TestProjectMemberResult> selectTestProjectMemberResultList(TestProjectMemberParams params);

    /**
     * 获取测试项目成员详细信息
     *
     * @param testProjectMemberId 测试项目成员主键
     * @return 测试项目成员Result
     */
    TestProjectMemberResult selectTestProjectMemberResult(Long testProjectMemberId);

    /**
     * 新增测试项目成员
     *
     * @param testProjectMember 测试项目成员
     * @return 结果
     */
    int insertTestProjectMember(TestProjectMember testProjectMember);

    /**
     * 修改测试项目成员
     *
     * @param testProjectMember 测试项目成员
     * @return 结果
     */
    int updateTestProjectMember(TestProjectMember testProjectMember);

    /**
     * 批量删除测试项目成员
     *
     * @param testProjectMemberIdList 需要删除的测试项目成员主键集合
     * @return 结果
     */
    int deleteTestProjectMemberByIdList(List<Long> testProjectMemberIdList);

    /**
     * 删除测试项目成员信息
     *
     * @param testProjectMemberId 测试项目成员主键
     * @return 结果
     */
    int deleteTestProjectMemberById(Long testProjectMemberId);

    /**
     * 修改测试项目成员为逻辑删除
     *
     * @param testProjectMemberId 测试项目成员ID
     * @return 结果
     */
    int logicDeleteTestProjectMemberById(Long testProjectMemberId);

    /**
     * 批量修改测试项目成员为逻辑删除
     *
     * @param testProjectMemberIdList 测试项目成员ID集合
     * @return 结果
     */
    int logicDeleteTestProjectMemberByIdList(List<Long> testProjectMemberIdList);

    /**
     * 查询测试项目成员数量
     *
     * @param params 测试项目成员Params
     * @return 数量
     */
    int selectTestProjectMemberCount(TestProjectMemberParams params);

    /**
     * 按条件查询单条测试项目成员
     *
     * @param params 测试项目成员Params
     * @return 测试项目成员
     */
    TestProjectMember selectTestProjectMemberOne(TestProjectMemberParams params);

    /**
     * 指定项目下有效成员的用户 ID
     *
     * @param testProjectId 测试项目 ID
     * @return userId 列表，不会为 null
     */
    List<Long> listMemberUserIdsByTestProjectId(Long testProjectId);

    /**
     * 按当前登录用户解析并校验其在项目中的成员角色。
     * 非成员或未登录时抛业务异常。
     *
     * @param testProjectId 项目 id
     * @return 成员角色
     */
    TestProjectMemberRole getCheckProjectMemberRole(Long testProjectId);

    /**
     * 按指定用户解析并校验其在项目中的成员角色。
     * 用户为空时抛「缺少审计用户/操作者」；非成员时抛无权限。
     * 平台超管用户直接视为系统管理员角色。
     *
     * @param testProjectId 项目 id
     * @param userId 操作者用户 id
     * @return 成员角色
     */
    TestProjectMemberRole getCheckProjectMemberRole(Long testProjectId, Long userId);

}
