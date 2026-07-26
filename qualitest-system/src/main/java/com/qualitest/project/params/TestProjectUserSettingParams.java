package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试项目用户设置 Params 对象
 *
 * @author qualitest
 * @since 2026-02-09
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectUserSettingParams")
public class TestProjectUserSettingParams extends BaseEntity implements Serializable {

    /**
     * 测试项目ID
     */
    private Long testProjectId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 项目Token
     */
    private String projectToken;

    /**
     * 测试项目环境ID
     */
    private Long testProjectEnvId;

}
