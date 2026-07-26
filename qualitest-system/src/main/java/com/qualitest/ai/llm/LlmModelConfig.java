package com.qualitest.ai.llm;

import lombok.Builder;
import lombok.Getter;

/**
 * 单次 LLM 调用所需的完整运行时配置。
 * <p>
 * 由 {@link com.qualitest.ai.service.IAiLlmModelService#resolve(Long)} 从厂商表、模型表与全局配置组装，
 * 包含连接地址、密钥、上游 model 名以及超时与 token 上限。
 */
@Getter
@Builder
public class LlmModelConfig {

    /** 模型主键，用于审计与前端展示 */
    private final Long aiLlmModelId;

    /** 所属厂商主键 */
    private final Long aiLlmVendorId;

    /** 上游请求体 model 字段，同时作为展示名 */
    private final String modelName;

    /** 厂商展示名 */
    private final String vendorName;

    /** 协议标识：{@link LlmProviderTypes#OPENAI_COMPATIBLE} 或 {@link LlmProviderTypes#ANTHROPIC_COMPATIBLE} */
    private final String provider;

    /** 厂商 API Base URL，不含末尾斜杠 */
    private final String baseUrl;

    /** 厂商 API Key，仅服务端使用，不得返回给前端 */
    private final String apiKey;

    /** 本次请求 max_tokens 上限 */
    private final int maxTokens;

    /** OkHttp 连接超时（毫秒） */
    private final int connectTimeoutMs;

    /** OkHttp 读超时（毫秒），LLM 慢响应时主要受此限制 */
    private final int readTimeoutMs;

    /** OkHttp 写超时（毫秒） */
    private final int writeTimeoutMs;

    /** 模型是否支持思考（库表 thinking_capable） */
    @Builder.Default
    private final boolean thinkingCapable = false;

    /** 模型默认是否开启思考 */
    @Builder.Default
    private final boolean thinkingDefault = false;

    /** 模型级思考 token 预算，null 时用全局默认 */
    private final Integer thinkingBudgetTokens;
}
