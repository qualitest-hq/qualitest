package com.qualitest.ai.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 模型解析查询结果：模型 JOIN 厂商后的内部传输对象。
 * <p>
 * 含 apiKey 等敏感字段，仅服务端调用 LLM 时使用，不得直接返回给前端列表接口。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiLlmModelResolveResult implements Serializable {

    /**
     * 模型主键
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmModelId;

    /**
     * 所属厂商主键
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmVendorId;

    /**
     * 上游模型 ID
     */
    private String modelName;

    /**
     * 展示名；为空时前端与分组列表回退为 modelName
     */
    private String displayName;

    /**
     * 厂商名称
     */
    private String vendorName;

    /**
     * 协议类型
     */
    private String provider;

    /**
     * API Base URL
     */
    private String baseUrl;

    /**
     * API 密钥明文
     */
    private String apiKey;

    /**
     * 模型启用状态（0禁用 1启用）
     */
    private Integer modelEnableStatus;

    /**
     * 厂商启用状态（0禁用 1启用）
     */
    private Integer vendorEnableStatus;

    /**
     * 模型删除状态（0正常 1删除）
     */
    private Integer modelDelStatus;

    /**
     * 厂商删除状态（0正常 1删除）
     */
    private Integer vendorDelStatus;

    /**
     * 模型排序权重
     */
    private Integer modelSortNum;

    /**
     * 厂商排序权重
     */
    private Integer vendorSortNum;

    /**
     * 是否支持思考模式（0否 1是）
     */
    private Integer thinkingCapable;

    /**
     * 默认是否开启思考（0关 1开）
     */
    private Integer thinkingDefault;

    /**
     * 思考模式 token 预算上限；为空时使用全局默认值
     */
    private Integer thinkingBudgetTokens;
}
