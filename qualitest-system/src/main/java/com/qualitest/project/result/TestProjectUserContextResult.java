package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户在指定测试项目下的上下文：角色、权限与个人设置。
 *
 * @author qualitest
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectUserContextResult")
public class TestProjectUserContextResult implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

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

    /** 成员角色标识 role_key；平台超管用户为 sysAdmin */
    private String memberRole;

    /**
     * 当前用户在该项目下已生效的权限 code 列表（前端门禁与按钮显隐）
     */
    private java.util.List<String> capabilities;

    /**
     * 当前用户在该项目下的用户设置（系统管理员为 null）
     */
    private TestProjectUserSettingResult setting;

}
