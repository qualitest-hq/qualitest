package com.qualitest.ai.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * AI提示词模板 Result 对象
 *
 * @author qualitest
 * @since 2026-07-04
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("AiPromptTemplateResult")
public class AiPromptTemplateResult implements Serializable {

    /**
     * 提示词模板ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiPromptTemplateId;

    /**
     * 模板范围（platform平台 project项目）
     */
    @Excel(name = "模板范围", readConverterExp = "platform=平台,project=项目")
    private String templateScope;

    /**
     * 测试项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /**
     * 会话场景
     */
    @Excel(name = "会话场景")
    private String sessionScene;

    /**
     * 模板标题
     */
    @Excel(name = "模板标题")
    private String templateTitle;

    /**
     * 模板说明
     */
    @Excel(name = "模板说明")
    private String templateDescription;

    /**
     * 模板正文
     */
    @Excel(name = "模板正文")
    private String templateContent;

    /**
     * 内置状态（0自定义 1内置）
     */
    @Excel(name = "内置状态", readConverterExp = "0=自定义,1=内置")
    private Integer builtinStatus;

    /**
     * 启用状态（0禁用 1启用）
     */
    @Excel(name = "启用状态", readConverterExp = "0=禁用,1=启用")
    private Integer enableStatus;

    /**
     * 排序
     */
    @Excel(name = "排序")
    private Integer sortNum;

    /**
     * 备注
     */
    @Excel(name = "备注")
    private String remark;


}
