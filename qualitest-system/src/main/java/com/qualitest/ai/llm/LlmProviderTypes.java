package com.qualitest.ai.llm;

/**
 * LLM 厂商协议标识常量。
 * <p>
 * 取值写入 ai_llm_vendor.provider，决定走哪套客户端与发现适配器：
 * <ul>
 *   <li>openai_compatible：Chat Completions（含 DeepSeek 等兼容厂商）</li>
 *   <li>anthropic_compatible：Anthropic Messages API</li>
 * </ul>
 */
public final class LlmProviderTypes {

    /** Chat Completions 协议标识。 */
    public static final String OPENAI_COMPATIBLE = "openai_compatible";

    /** Anthropic Messages API 协议标识。 */
    public static final String ANTHROPIC_COMPATIBLE = "anthropic_compatible";

    private LlmProviderTypes() {
    }

    /** 是否为 Anthropic Messages 协议。 */
    public static boolean isAnthropic(String provider) {
        return ANTHROPIC_COMPATIBLE.equals(provider);
    }

    /**
     * 是否为 Chat Completions 协议。
     * 空值或未填写时默认按 Chat Completions 处理。
     */
    public static boolean isOpenAiCompatible(String provider) {
        return provider == null
                || provider.isBlank()
                || OPENAI_COMPATIBLE.equals(provider);
    }
}
