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
 * 测试环境插件绑定对象 test_plugin_env_binding
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
@Alias("TestPluginEnvBinding")
public class TestPluginEnvBinding extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 环境插件绑定ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testPluginEnvBindingId;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 测试项目环境ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectEnvId;

    /**
     * 插件标识
     */
    private String pluginId;

    /**
     * 启用状态（0禁用 1启用）
     */
    private Integer enabled;

    /**
     * 环境插件配置
     */
    private String configJson;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
