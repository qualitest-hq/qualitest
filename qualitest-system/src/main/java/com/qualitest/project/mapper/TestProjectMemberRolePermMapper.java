package com.qualitest.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.project.domain.TestProjectMemberRolePerm;
import com.qualitest.project.params.TestProjectMemberRolePermParams;
import com.qualitest.project.result.TestProjectMemberRolePermResult;

/**
 * 测试项目成员角色权限Mapper接口
 * 
 * @author qualitest
 * @date 2026-10-29
 */
@Mapper
public interface TestProjectMemberRolePermMapper {
    /**
     * 查询测试项目成员角色权限列表
     *
     * @param testProjectMemberRolePerm 测试项目成员角色权限
     * @return 测试项目成员角色权限集合
     */
    List<TestProjectMemberRolePerm> selectTestProjectMemberRolePermList(TestProjectMemberRolePerm testProjectMemberRolePerm);

    /**
     * 查询测试项目成员角色权限
     *
     * @param testProjectMemberRolePermId 测试项目成员角色权限主键
     * @return 测试项目成员角色权限
     */
    TestProjectMemberRolePerm selectTestProjectMemberRolePermById(Long testProjectMemberRolePermId);

    /**
     * 查询测试项目成员角色权限Result列表
     *
     * @param params 测试项目成员角色权限Params
     * @return 测试项目成员角色权限Result集合
     */
    List<TestProjectMemberRolePermResult> selectTestProjectMemberRolePermResultList(TestProjectMemberRolePermParams params);

    /**
     * 获取测试项目成员角色权限详细信息
     *
     * @param testProjectMemberRolePermId 测试项目成员角色权限主键
     * @return 测试项目成员角色权限Result
     */
    TestProjectMemberRolePermResult selectTestProjectMemberRolePermResult(Long testProjectMemberRolePermId);

    /**
     * 查询测试项目成员角色权限数量
     *
     * @param params 测试项目成员角色权限Params
     * @return 数量
     */
    int selectTestProjectMemberRolePermCount(TestProjectMemberRolePermParams params);

    /**
     * 按条件查询单条测试项目成员角色权限
     *
     * @param params 测试项目成员角色权限Params
     * @return 测试项目成员角色权限
     */
    TestProjectMemberRolePerm selectTestProjectMemberRolePermOne(TestProjectMemberRolePermParams params);

    /**
     * 新增测试项目成员角色权限
     * 
     * @param testProjectMemberRolePerm 测试项目成员角色权限
     * @return 结果
     */
    int insertTestProjectMemberRolePerm(TestProjectMemberRolePerm testProjectMemberRolePerm);

    /**
     * 修改测试项目成员角色权限
     * 
     * @param testProjectMemberRolePerm 测试项目成员角色权限
     * @return 结果
     */
    int updateTestProjectMemberRolePerm(TestProjectMemberRolePerm testProjectMemberRolePerm);

    /**
     * 删除测试项目成员角色权限
     * 
     * @param testProjectMemberRolePermId 测试项目成员角色权限主键
     * @return 结果
     */
    int deleteTestProjectMemberRolePermById(Long testProjectMemberRolePermId);

    /**
     * 批量删除测试项目成员角色权限
     * 
     * @param testProjectMemberRolePermIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestProjectMemberRolePermByIdList(@Param("list") List<Long> testProjectMemberRolePermIdList);

    /**
     * 按角色删除全部权限
     *
     * @param testProjectMemberRoleId 角色ID
     * @return 结果
     */
    int deleteTestProjectMemberRolePermByRoleId(@Param("testProjectMemberRoleId") Long testProjectMemberRoleId);

    /**
     * 按角色查询权限 code 列表
     *
     * @param testProjectMemberRoleId 角色ID
     * @return perm_code 列表
     */
    List<String> selectPermCodesByRoleId(@Param("testProjectMemberRoleId") Long testProjectMemberRoleId);
}
