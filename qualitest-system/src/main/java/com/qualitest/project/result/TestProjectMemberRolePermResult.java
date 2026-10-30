package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 测试项目成员角色权限 Result 对象
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
@Alias("TestProjectMemberRolePermResult")
public class TestProjectMemberRolePermResult implements Serializable {

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
    @Excel(name = "权限编码")
    private String permCode;


}
