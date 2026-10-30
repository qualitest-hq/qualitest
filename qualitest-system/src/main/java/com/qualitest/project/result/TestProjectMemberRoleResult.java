package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 测试项目成员角色 Result 对象
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
@Alias("TestProjectMemberRoleResult")
public class TestProjectMemberRoleResult implements Serializable {

    /**
     * 项目成员角色ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectMemberRoleId;

    /**
     * 角色标识
     */
    @Excel(name = "角色标识")
    private String roleKey;

    /**
     * 角色名称
     */
    @Excel(name = "角色名称")
    private String roleName;

    /**
     * 排序
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long sortNum;

    /**
     * 启用状态（0禁用 1启用）
     */
    @Excel(name = "启用状态", readConverterExp = "0=禁用,1=启用")
    private Long enableStatus;

    /**
     * 备注
     */
    @Excel(name = "备注")
    private String remark;

    /**
     * 该角色已配置的权限 code 列表
     */
    private java.util.List<String> permCodes;

}
