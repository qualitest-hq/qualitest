package com.qualitest.ai.llm;

import lombok.Builder;
import lombok.Getter;

/**
 * 单次 LLM 调用的运行时配置：连接信息、上游 model 名、超时、token 上限与思考相关能力。
 * 由模型服务按模型主键 JOIN 厂商表后组装。
 */
@Getter
@Builder
public class LlmModelConfig {

    /** 模型主键，用于审计与前端展示 */
    private final Long aiLlmModelId;

    /** 所属厂商主键 */
    private final Long aiLlmVendorId;

    /** 上游请求体 model 字段 */
    private final String modelName;

    /** 厂商展示名 */
    private final String vendorName;

    /** 协议标识：openai_compatible 或 anthropic_compatible */
    private final String provider;

    /** 厂商 API Base URL，不含末尾斜杠 */
    private final String baseUrl;

    /** 厂商 API Key，仅服务端使用，不得返回给前端 */
    private final String apiKey;

    /** 本次请求 max_tokens 上限 */
    private final int maxTokens;

    /** 连接超时（毫秒） */
    private final int connectTimeoutMs;

    /** 读超时（毫秒），慢响应主要受此限制 */
    private final int readTimeoutMs;

    /** 写超时（毫秒） */
    private final int writeTimeoutMs;

    /** 模型是否支持思考（来自库表 thinking_capable） */
    @Builder.Default
    private final boolean thinkingCapable = false;

    /** 选中该模型时默认是否开启思考 */
    @Builder.Default
    private final boolean thinkingDefault = false;

    /**
     * 思考请求风格，决定请求体如何开关思考。
     * 空：不写思考字段；
     * openai_reasoning_effort：开启时写 reasoning_effort；
     * deepseek_thinking：用 thinking.type 显式 enabled/disabled。
     */
    private final String thinkingControl;

    /** 模型级思考 token 预算；为空时用全局默认 */
    private final Integer thinkingBudgetTokens;
}
