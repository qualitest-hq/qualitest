package com.qualitest.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.qualitest.project.domain.TestProjectMemberRole;
import com.qualitest.project.params.TestProjectMemberRoleParams;
import com.qualitest.project.result.TestProjectMemberRoleResult;

/**
 * 测试项目成员角色Mapper接口
 * 
 * @author qualitest
 * @date 2026-10-29
 */
@Mapper
public interface TestProjectMemberRoleMapper {
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
     * 删除测试项目成员角色
     * 
     * @param testProjectMemberRoleId 测试项目成员角色主键
     * @return 结果
     */
    int deleteTestProjectMemberRoleById(Long testProjectMemberRoleId);

    /**
     * 批量删除测试项目成员角色
     * 
     * @param testProjectMemberRoleIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestProjectMemberRoleByIdList(@Param("list") List<Long> testProjectMemberRoleIdList);

    /**
     * 逻辑删除测试项目成员角色
     * 
     * @param testProjectMemberRoleId 测试项目成员角色主键
     * @return 结果
     */
    int logicDeleteTestProjectMemberRoleById(Long testProjectMemberRoleId);

    /**
     * 批量逻辑删除测试项目成员角色
     * 
     * @param testProjectMemberRoleIdList 测试项目成员角色主键集合
     * @return 结果
     */
    int logicDeleteTestProjectMemberRoleByIdList(@Param("list") List<Long> testProjectMemberRoleIdList);
}
