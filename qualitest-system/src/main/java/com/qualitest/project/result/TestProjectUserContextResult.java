package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.project.enums.TestProjectMemberRole;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户在指定测试项目下的综合信息
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectUserContextResult")
public class TestProjectUserContextResult implements Serializable {

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

    /**
     * 成员角色
     *
     * @see TestProjectMemberRole
     */
    private String memberRole;

    /**
     * 当前用户在该项目下的用户设置（系统管理员为 null）
     */
    private TestProjectUserSettingResult setting;

}
