package com.qualitest.project.params;

import com.qualitest.common.core.domain.BaseEntity;
import lombok.*;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;

/**
 * 平台插件包登记 Params 对象
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
@Alias("TestPluginPackageParams")
public class TestPluginPackageParams extends BaseEntity implements Serializable {

    /**
     * 插件标识
     */
    private String pluginId;

    /**
     * 语义化版本
     */
    private String version;

    /**
     * 展示名
     */
    private String displayName;

    /**
     * 整包内容指纹
     */
    private String contentHash;

}
