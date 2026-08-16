package com.qualitest.project.mapper;

import com.qualitest.project.domain.TestProjectMember;
import com.qualitest.project.params.TestProjectMemberParams;
import com.qualitest.project.result.TestProjectMemberResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 测试项目成员Mapper接口
 *
 * @author qualitest
 * @date 2026-02-05
 */
@Mapper
public interface TestProjectMemberMapper {

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
     * @param testProjectMemberIdList 需要删除的数据主键集合
     * @return 结果
     */
    int deleteTestProjectMemberByIdList(@Param("list") List<Long> testProjectMemberIdList);

    /**
     * 删除测试项目成员
     *
     * @param testProjectMemberId 测试项目成员主键
     * @return 结果
     */
    int deleteTestProjectMemberById(Long testProjectMemberId);

    /**
     * 逻辑删除测试项目成员
     *
     * @param testProjectMemberId 测试项目成员主键
     * @return 结果
     */
    int logicDeleteTestProjectMemberById(Long testProjectMemberId);

    /**
     * 批量逻辑删除测试项目成员
     *
     * @param testProjectMemberIdList 测试项目成员主键集合
     * @return 结果
     */
    int logicDeleteTestProjectMemberByIdList(@Param("list") List<Long> testProjectMemberIdList);

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
     * 查询指定项目下有效成员的用户 ID 列表
     *
     * @param testProjectId 测试项目 ID
     * @return user_id 列表，无成员时为空列表
     */
    List<Long> selectMemberUserIdsByTestProjectId(@Param("testProjectId") Long testProjectId);

    /**
     * 将项目下其他有效所有者降为管理员（排除新所有者用户）。
     *
     * @param testProjectId   测试项目 ID
     * @param excludeUserId   不降级的用户 ID（新所有者）
     * @return 更新行数
     */
    int demoteOtherOwnersToAdmin(@Param("testProjectId") Long testProjectId,
                                 @Param("excludeUserId") Long excludeUserId);

    /**
     * 统计项目下有效所有者人数
     */
    int countOwners(@Param("testProjectId") Long testProjectId);
}
