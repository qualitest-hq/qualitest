package com.qualitest.project.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 测试项目成员角色保存入参（含权限）
 *
 * @author qualitest
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TestProjectMemberRoleSaveParams implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目成员角色ID（修改时必填）
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
     * 备注
     */
    private String remark;

    /**
     * 权限 code 列表
     */
    private List<String> permCodes;
}
