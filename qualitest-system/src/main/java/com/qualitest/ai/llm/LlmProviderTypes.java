package com.qualitest.ai.llm;

/**
 * LLM 厂商协议标识常量。
 * <p>
 * 取值写入 {@code ai_llm_vendor.provider}，运行时由 {@link RoutingLlmProvider} 选择具体 HTTP 客户端。
 */
public final class LlmProviderTypes {

    /** Chat Completions 协议：POST /chat/completions */
    public static final String OPENAI_COMPATIBLE = "openai_compatible";

    /** Messages API 协议：POST /v1/messages */
    public static final String ANTHROPIC_COMPATIBLE = "anthropic_compatible";

    private LlmProviderTypes() {
    }

    /** 判断是否为 Anthropic Messages API 协议 */
    public static boolean isAnthropic(String provider) {
        return ANTHROPIC_COMPATIBLE.equals(provider);
    }

    /**
     * 判断是否为 Chat Completions 协议。
     * 空值或未填写时默认按 Chat Completions 处理。
     */
    public static boolean isOpenAiCompatible(String provider) {
        return provider == null
                || provider.isBlank()
                || OPENAI_COMPATIBLE.equals(provider);
    }
}
