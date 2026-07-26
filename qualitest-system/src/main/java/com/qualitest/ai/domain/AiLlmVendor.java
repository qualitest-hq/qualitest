package com.qualitest.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.ibatis.type.Alias;
import com.qualitest.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;

/**
 * AI 厂商对象 ai_llm_vendor
 * 
 * @author qualitest
 * @date 2026-06-15
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Alias("AiLlmVendor")
public class AiLlmVendor extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 厂商ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmVendorId;

    /**
     * 厂商名
     */
    private String vendorName;

    /**
     * 内置状态（0自定义 1内置）；内置记录由系统预置，不可删除
     */
    private Integer builtinStatus;

    /**
     * 内置厂商模板标识，关联预置的协议、发现策略与默认地址配置
     */
    private String templateId;

    /**
     * 协议标识。
     * 取值见 {@link LlmProviderTypes}，决定 LLM HTTP 客户端与请求路径。
     */
    private String provider;

    /**
     * 模型发现策略（openai_models、anthropic_models、ollama_tags、static_list、none）；
     * 自定义厂商可单独指定，内置厂商一般继承模板配置
     */
    private String discoveryType;

    /**
     * API Base URL
     */
    private String baseUrl;

    /**
     * 密钥明文
     */
    private String apiKey;

    /**
     * 启用状态（0禁用 1启用）
     */
    private Integer enableStatus;

    /**
     * 排序
     */
    private Integer sortNum;

    /**
     * 删除状态（0正常 1删除）
     */
    private Integer delStatus;

}
