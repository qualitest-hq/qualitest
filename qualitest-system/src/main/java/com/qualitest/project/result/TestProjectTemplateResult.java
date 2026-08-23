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
 * 项目模板查询结果。
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

    @Excel(name = "模板名称")
    private String templateName;

    @Excel(name = "鉴权头名称")
    private String headerName;

    @Excel(name = "鉴权头值模板")
    private String headerValueTemplate;

    /** 路径匹配 JSON。 */
    private String matchConfig;

    /** 预制接口 JSON 数组。 */
    private String apis;

    @Excel(name = "内置状态", readConverterExp = "0=自定义,1=内置")
    private Integer builtinStatus;

    @Excel(name = "启用状态", readConverterExp = "0=禁用,1=启用")
    private Integer enableStatus;

    @Excel(name = "排序")
    private Integer sortNum;

    @Excel(name = "备注")
    private String remark;

    /** 删除标记：0 正常，1 已删。 */
    private Integer delStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
