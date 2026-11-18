package com.qualitest.project.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import com.qualitest.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;

/**
 * 测试项目插件挂载对象 test_plugin_project_mount
 * 
 * @author qualitest
 * @date 2026-11-16
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestPluginProjectMount")
public class TestPluginProjectMount extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目插件挂载ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testPluginProjectMountId;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 插件标识
     */
    private String pluginId;

    /**
     * 挂载配置
     */
    private String mountJson;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
