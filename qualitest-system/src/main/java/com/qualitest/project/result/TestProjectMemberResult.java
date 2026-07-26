package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.annotation.Excel;
import com.qualitest.project.enums.TestProjectMemberRole;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;
import java.util.Date;

/**
 * 测试项目成员列表/导出等场景使用的查询结果对象（含关联项目名、用户信息等）
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
@Alias("TestProjectMemberResult")
public class TestProjectMemberResult implements Serializable {

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
     * 测试项目名
     */
    @Excel(name = "测试项目")
    private String projectName;

    /**
     * 用户ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 用户名称（昵称）
     */
    @Excel(name = "用户名称")
    private String nickName;

    /**
     * 登录名称
     */
    @Excel(name = "登录名称")
    private String userName;

    /**
     * 成员角色
     *
     * @see TestProjectMemberRole
     */
    @Excel(name = "成员角色", readConverterExp = "owner=所有者,admin=管理员,developer=开发者,tester=测试员")
    private String memberRole;

    /**
     * 删除状态（0正常 1删除）
     */
    @Excel(name = "删除状态", readConverterExp = "0=正常,1=删除")
    private Integer delStatus;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "创建时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "更新时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
