package com.qualitest.project.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.common.annotation.Excel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;

import java.io.Serializable;
import java.util.Date;

/**
 * 项目模板列表 / 详情查询结果（给前端与导出用）。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("TestProjectTemplateResult")
public class TestProjectTemplateResult implements Serializable {

    /** 模板主键。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectTemplateId;

    /** 模板名称。 */
    @Excel(name = "模板名称")
    private String templateName;

    /** 路径匹配 JSON（pathPrefix 等）。 */
    private String matchConfig;

    /** 预制接口 JSON 数组。 */
    private String templateApis;

    /** 预制参数 JSON 数组。 */
    private String templateParams;

    /** 预制测试流 JSON 数组。 */
    private String templateFlows;

    /** 预制 AI 提示词 JSON 数组。 */
    private String templatePrompts;

    /** 内置标记：0 自定义，1 内置。 */
    @Excel(name = "内置状态", readConverterExp = "0=自定义,1=内置")
    private Integer builtinStatus;

    /** 启用标记：0 禁用，1 启用。 */
    @Excel(name = "启用状态", readConverterExp = "0=禁用,1=启用")
    private Integer enableStatus;

    /** 列表排序。 */
    @Excel(name = "排序")
    private Integer sortNum;

    /** 备注。 */
    @Excel(name = "备注")
    private String remark;

    /** 删除标记：0 正常，1 已删。 */
    private Integer delStatus;

    /** 创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 最近修改时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
