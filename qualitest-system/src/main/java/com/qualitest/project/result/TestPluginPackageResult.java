package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 平台插件包登记 Result 对象
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
@Alias("TestPluginPackageResult")
public class TestPluginPackageResult implements Serializable {

    /**
     * 插件包登记ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testPluginPackageId;

    /**
     * 插件标识
     */
    @Excel(name = "插件标识")
    private String pluginId;

    /**
     * 语义化版本
     */
    @Excel(name = "语义化版本")
    private String version;

    /**
     * 展示名
     */
    @Excel(name = "展示名")
    private String displayName;

    /**
     * 说明书快照
     */
    @Excel(name = "说明书快照")
    private String manifestJson;

    /**
     * 整包内容指纹
     */
    @Excel(name = "整包内容指纹")
    private String contentHash;


}
