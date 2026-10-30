package com.qualitest.project.service;

import java.util.List;
import java.util.Set;
import com.qualitest.project.domain.TestProjectMemberRole;
import com.qualitest.project.params.TestProjectMemberRoleParams;
import com.qualitest.project.params.TestProjectMemberRoleSaveParams;
import com.qualitest.project.result.TestProjectMemberRoleResult;

/**
 * 测试项目成员角色Service接口
 * 
 * @author qualitest
 * @date 2026-10-29
 */
public interface ITestProjectMemberRoleService {

    /**
     * 按角色标识查询启用中的权限集合（含缓存）
     *
     * @param roleKey 角色标识
     * @return 权限 code；角色不存在或已禁用时为空集
     */
    Set<String> listPermCodesByRoleKey(String roleKey);

    /**
     * 失效全部角色权限缓存
     */
    void clearPermCache();

    /**
     * 新增角色并保存权限
     *
     * @param params 保存入参
     * @return 结果
     */
    int insertRoleWithPerms(TestProjectMemberRoleSaveParams params);

    /**
     * 修改角色并全量替换权限；角色标识变更时同步更新成员上的 role_key
     *
     * @param params 保存入参
     * @return 结果
     */
    int updateRoleWithPerms(TestProjectMemberRoleSaveParams params);

    /**
     * 启用中的角色列表（成员下拉）
     *
     * @return 角色 Result 列表
     */
    List<TestProjectMemberRoleResult> listEnabledRoles();

    /**
     * 查询测试项目成员角色列表
     *
     * @param testProjectMemberRole 测试项目成员角色
     * @return 测试项目成员角色集合
     */
    List<TestProjectMemberRole> selectTestProjectMemberRoleList(TestProjectMemberRole testProjectMemberRole);

    /**
     * 查询测试项目成员角色
     *
     * @param testProjectMemberRoleId 测试项目成员角色主键
     * @return 测试项目成员角色
     */
    TestProjectMemberRole selectTestProjectMemberRoleById(Long testProjectMemberRoleId);

    /**
     * 查询测试项目成员角色Result列表
     *
     * @param params 测试项目成员角色Params
     * @return 测试项目成员角色Result集合
     */
    List<TestProjectMemberRoleResult> selectTestProjectMemberRoleResultList(TestProjectMemberRoleParams params);

    /**
     * 获取测试项目成员角色详细信息
     *
     * @param testProjectMemberRoleId 测试项目成员角色主键
     * @return 测试项目成员角色Result
     */
    TestProjectMemberRoleResult selectTestProjectMemberRoleResult(Long testProjectMemberRoleId);

    /**
     * 新增测试项目成员角色
     * 
     * @param testProjectMemberRole 测试项目成员角色
     * @return 结果
     */
    int insertTestProjectMemberRole(TestProjectMemberRole testProjectMemberRole);

    /**
     * 修改测试项目成员角色
     * 
     * @param testProjectMemberRole 测试项目成员角色
     * @return 结果
     */
    int updateTestProjectMemberRole(TestProjectMemberRole testProjectMemberRole);

    /**
     * 批量删除测试项目成员角色
     * 
     * @param testProjectMemberRoleIdList 需要删除的测试项目成员角色主键集合
     * @return 结果
     */
    int deleteTestProjectMemberRoleByIdList(List<Long> testProjectMemberRoleIdList);

    /**
     * 删除测试项目成员角色信息
     * 
     * @param testProjectMemberRoleId 测试项目成员角色主键
     * @return 结果
     */
    public int deleteTestProjectMemberRoleById(Long testProjectMemberRoleId);

    /**
     * 修改测试项目成员角色为逻辑删除
     *
     * @param testProjectMemberRoleId 测试项目成员角色ID
     * @return 结果
     */
    int logicDeleteTestProjectMemberRoleById(Long testProjectMemberRoleId);

    /**
     * 批量修改测试项目成员角色为逻辑删除
     *
     * @param testProjectMemberRoleIdList 测试项目成员角色ID集合
     * @return 结果
     */
    int logicDeleteTestProjectMemberRoleByIdList(List<Long> testProjectMemberRoleIdList);

    /**
     * 查询测试项目成员角色数量
     *
     * @param params 测试项目成员角色Params
     * @return 数量
     */
    int selectTestProjectMemberRoleCount(TestProjectMemberRoleParams params);

    /**
     * 按条件查询单条测试项目成员角色
     *
     * @param params 测试项目成员角色Params
     * @return 测试项目成员角色
     */
    TestProjectMemberRole selectTestProjectMemberRoleOne(TestProjectMemberRoleParams params);
}
