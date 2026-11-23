package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试环境插件绑定 Params 对象
 *
 * @author qualitest
 * @since 2026-11-16
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestPluginEnvBindingParams")
public class TestPluginEnvBindingParams extends BaseEntity implements Serializable {

    /**
     * 测试项目ID
     */
    private Long testProjectId;

    /**
     * 测试项目环境ID
     */
    private Long testProjectEnvId;

    /**
     * 插件标识
     */
    private String pluginId;

    /**
     * 启用状态（0禁用 1启用）
     */
    private Integer enabled;

}
