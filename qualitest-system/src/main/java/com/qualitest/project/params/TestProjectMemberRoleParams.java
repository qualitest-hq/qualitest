package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试项目成员角色 Params 对象
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
@Alias("TestProjectMemberRoleParams")
public class TestProjectMemberRoleParams extends BaseEntity implements Serializable {

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
    private Long sortNum;

    /**
     * 启用状态（0禁用 1启用）
     */
    private Long enableStatus;

    /**
     * 删除状态（0正常 1删除）
     */
    private Long delStatus;

}
