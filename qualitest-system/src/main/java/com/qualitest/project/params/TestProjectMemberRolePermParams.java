package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试项目成员角色权限 Params 对象
 *
 * @author qualitest
 * @since 2026-10-29
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectMemberRolePermParams")
public class TestProjectMemberRolePermParams extends BaseEntity implements Serializable {

    /**
     * 项目成员角色ID
     */
    private Long testProjectMemberRoleId;

    /**
     * 权限编码
     */
    private String permCode;

}
