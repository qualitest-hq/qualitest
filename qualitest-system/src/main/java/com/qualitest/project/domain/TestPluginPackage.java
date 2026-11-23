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
 * 平台插件包登记对象 test_plugin_package
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
@Alias("TestPluginPackage")
public class TestPluginPackage extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 插件包登记ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testPluginPackageId;

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
     * 说明书快照
     */
    private String manifestJson;

    /**
     * 整包内容指纹
     */
    private String contentHash;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
