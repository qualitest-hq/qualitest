package com.qualitest.project.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import com.qualitest.project.enums.TestProjectMemberRole;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serial;
import java.util.List;

/**
 * 测试项目成员对象 test_project_member
 *
 * @author qualitest
 * @date 2026-02-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectMember")
public class TestProjectMember extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目成员ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectMemberId;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 用户ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 成员角色
     *
     * @see TestProjectMemberRole
     */
    private String memberRole;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

    // --------------------------- 非数据库字段 ---------------------------

    /**
     * 项目成员ID列表
     */
    private List<Long> testProjectMemberIdList;

}
