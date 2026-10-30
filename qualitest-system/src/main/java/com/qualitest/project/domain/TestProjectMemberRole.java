package com.qualitest.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import com.qualitest.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;

/**
 * 测试项目成员角色对象 test_project_member_role
 * 
 * @author qualitest
 * @date 2026-10-29
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectMemberRole")
public class TestProjectMemberRole extends BaseEntity {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目成员角色ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectMemberRoleId;

    /**
     * 角色标识
     */
    private String roleKey;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 排序
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long sortNum;

    /**
     * 启用状态（0禁用 1启用）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long enableStatus;

    /**
     * 删除状态（0正常 1删除）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long delStatus;

}
