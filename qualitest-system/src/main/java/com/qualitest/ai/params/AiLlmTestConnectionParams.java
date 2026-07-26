package com.qualitest.ai.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 厂商连通性检测请求，仅用于保存前的临时校验，不写入数据库。
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiLlmTestConnectionParams implements Serializable {

    /**
     * 内置厂商模板标识；选择模板时自动填充 provider、discoveryType 等字段
     */
    private String templateId;

    /**
     * 协议类型（如 openai_compatible、anthropic_compatible）
     */
    private String provider;

    /**
     * 模型发现策略（openai_models、anthropic_models、ollama_tags、static_list、none）
     */
    private String discoveryType;

    /**
     * API Base URL
     */
    private String baseUrl;

    /**
     * API 密钥
     */
    private String apiKey;

    /**
     * 已保存的厂商 ID；编辑场景下 apiKey 为空或为脱敏占位时，从库内读取真实密钥
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmVendorId;
}
