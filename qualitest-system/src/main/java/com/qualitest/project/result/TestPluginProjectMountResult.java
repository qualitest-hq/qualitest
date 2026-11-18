package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 测试项目插件挂载 Result 对象
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
@Alias("TestPluginProjectMountResult")
public class TestPluginProjectMountResult implements Serializable {

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
    @Excel(name = "插件标识")
    private String pluginId;

    /**
     * 挂载配置
     */
    @Excel(name = "挂载配置")
    private String mountJson;


}
