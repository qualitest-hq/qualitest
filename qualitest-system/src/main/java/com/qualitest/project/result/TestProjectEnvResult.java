package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * 测试项目环境 Result 对象
 *
 * @author qualitest
 * @since 2026-02-05
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectEnvResult")
public class TestProjectEnvResult implements Serializable {

    /**
     * 测试项目环境ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectEnvId;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 用户ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Excel(name = "用户ID")
    private Long userId;

    /**
     * 共享状态（share 共享，private 私有）
     */
    @Excel(name = "共享状态", readConverterExp = "share=共享,private=私有")
    private String shareStatus;

    /**
     * 环境标识颜色
     */
    @Excel(name = "环境颜色")
    private String envColor;

    /**
     * 环境名称
     */
    @Excel(name = "环境名称")
    private String envName;

    /**
     * 环境URL
     */
    @Excel(name = "环境URL")
    private String envUrl;

    /**
     * 环境变量
     */
    @Excel(name = "环境变量")
    private String envVariables;

    /**
     * 是否允许在正式 Run 中还原被测数据（0 否，1 是）
     */
    @Excel(name = "允许还原", readConverterExp = "0=否,1=是")
    private Integer allowDestructiveReset;

    /**
     * 排序号
     */
    @Excel(name = "排序号")
    private Integer sortNum;


}
