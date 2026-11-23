package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 测试环境插件绑定 Result 对象
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
@Alias("TestPluginEnvBindingResult")
public class TestPluginEnvBindingResult implements Serializable {

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
    @Excel(name = "插件标识")
    private String pluginId;

    /**
     * 启用状态（0禁用 1启用）
     */
    @Excel(name = "启用状态", readConverterExp = "0=禁用,1=启用")
    private Integer enabled;

    /**
     * 环境插件配置
     */
    @Excel(name = "环境插件配置")
    private String configJson;


}
