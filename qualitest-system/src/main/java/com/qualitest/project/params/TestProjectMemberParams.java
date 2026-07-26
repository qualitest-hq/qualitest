package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import com.qualitest.project.enums.TestProjectMemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试项目成员查询参数（列表筛选、统计、按条件查单条等）
 *
 * @author qualitest
 * @since 2026-02-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectMemberParams")
public class TestProjectMemberParams extends BaseEntity implements Serializable {

    /**
     * 项目成员ID
     */
    private Long testProjectMemberId;

    /**
     * 测试项目ID
     */
    @NotNull(message = "测试项目ID不能为空")
    private Long testProjectId;

    /**
     * 项目名（模糊查询）
     */
    private String projectName;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名称（按昵称模糊查）
     */
    private String userName;

    /**
     * 成员角色
     *
     * @see TestProjectMemberRole
     */
    private String memberRole;

    /**
     * 删除状态（0正常 1删除）；不传时仅查未删除
     */
    private Integer delStatus;

}
