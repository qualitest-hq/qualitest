package com.qualitest.ai.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * AI 模型查询/导出结果对象，用于管理端列表与详情展示。
 *
 * @author qualitest
 * @since 2026-06-15
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("AiLlmModelResult")
public class AiLlmModelResult implements Serializable {

    /**
     * 模型ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmModelId;

    /**
     * 厂商ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmVendorId;

    /**
     * 厂商名
     */
    @Excel(name = "厂商名")
    private String vendorName;

    /**
     * 上游模型 ID，调用 LLM API 时作为 model 参数
     */
    @Excel(name = "模型名")
    private String modelName;

    /**
     * 界面展示名称；为空时回退使用 modelName
     */
    @Excel(name = "展示名")
    private String displayName;

    /**
     * 内置状态（0自定义 1内置）；内置记录由系统预置，可删除后重新获取
     */
    @Excel(name = "内置状态", readConverterExp = "0=自定义,1=内置")
    private Integer builtinStatus;

    /**
     * 启用状态（0禁用 1启用）
     */
    @Excel(name = "启用状态", readConverterExp = "0=禁用,1=启用")
    private Integer enableStatus;

    /**
     * 是否支持思考模式（0否 1是）
     */
    @Excel(name = "支持思考", readConverterExp = "0=否,1=是")
    private Integer thinkingCapable;

    /**
     * 选中该模型时默认是否开启思考（0关 1开）
     */
    @Excel(name = "默认思考", readConverterExp = "0=关,1=开")
    private Integer thinkingDefault;

    /**
     * 思考模式 token 预算上限；为空时使用全局默认值
     */
    private Integer thinkingBudgetTokens;

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
