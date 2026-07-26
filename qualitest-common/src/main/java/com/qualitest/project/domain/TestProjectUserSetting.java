package com.qualitest.project.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serial;

/**
 * 测试项目用户设置对象 test_project_user_setting
 *
 * @author qualitest
 * @date 2026-02-09
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectUserSetting")
public class TestProjectUserSetting extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 测试项目用户设置ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectUserSettingId;

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
     * 项目Token
     */
    private String projectToken;

    /**
     * 测试项目环境ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectEnvId;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
