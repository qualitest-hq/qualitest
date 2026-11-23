package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 测试项目插件挂载 Params 对象
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
@Alias("TestPluginProjectMountParams")
public class TestPluginProjectMountParams extends BaseEntity implements Serializable {

    /**
     * 测试项目ID
     */
    private Long testProjectId;

    /**
     * 插件标识
     */
    private String pluginId;

    /**
     * 挂载配置
     */
    private String mountJson;

}
