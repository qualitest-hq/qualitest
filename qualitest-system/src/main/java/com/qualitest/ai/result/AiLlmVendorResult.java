package com.qualitest.ai.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.Alias;
import java.util.Date;
import java.math.BigDecimal;
import com.qualitest.common.annotation.Excel;

import java.io.Serializable;

/**
 * AI 厂商查询/导出结果对象，用于管理端列表与详情展示。
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
@Alias("AiLlmVendorResult")
public class AiLlmVendorResult implements Serializable {

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
     * 内置状态（0自定义 1内置）；内置记录由系统预置，不可删除
     */
    @Excel(name = "内置状态", readConverterExp = "0=自定义,1=内置")
    private Integer builtinStatus;

    /**
     * 内置厂商模板标识，用于初始化协议类型、模型发现策略和默认 API 地址
     */
    private String templateId;

    /**
     * 模型发现策略（openai_models、anthropic_models、ollama_tags、static_list、none）
     */
    private String discoveryType;

    /**
     * 协议标识
     */
    @Excel(name = "协议标识")
    private String provider;

    /**
     * API Base URL
     */
    @Excel(name = "API Base URL")
    private String baseUrl;

    /**
     * 密钥明文
     */
    @Excel(name = "密钥明文")
    private String apiKey;

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
