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
 * 测试项目成员角色权限对象 test_project_member_role_perm
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
@Alias("TestProjectMemberRolePerm")
public class TestProjectMemberRolePerm extends BaseEntity {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色权限ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectMemberRolePermId;

    /**
     * 项目成员角色ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectMemberRoleId;

    /**
     * 权限编码
     */
    private String permCode;

}
