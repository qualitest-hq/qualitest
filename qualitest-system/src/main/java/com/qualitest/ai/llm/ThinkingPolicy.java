package com.qualitest.ai.llm;

import com.qualitest.ai.config.AiLlmConfigService;

/**
 * 思考模式生效策略：模型能力 × 会话/请求开关。
 */
public final class ThinkingPolicy {

    private ThinkingPolicy() {
    }

    /** 协议层是否具备思考 API 能力 */
    public static boolean isProviderThinkingCapable(String provider) {
        return LlmProviderTypes.isAnthropic(provider) || LlmProviderTypes.isOpenAiCompatible(provider);
    }

    /**
     * 计算本次请求是否启用思考。
     *
     * @param modelConfig           已 resolve 的模型配置
     * @param sessionThinkingEnabled 会话开关：0 关、1 开、null 跟随模型默认
     */
    public static boolean resolveEffective(LlmModelConfig modelConfig, Integer sessionThinkingEnabled) {
        if (modelConfig == null || !modelConfig.isThinkingCapable()) {
            return false;
        }
        if (!isProviderThinkingCapable(modelConfig.getProvider())) {
            return false;
        }
        if (sessionThinkingEnabled != null) {
            return sessionThinkingEnabled == 1;
        }
        return modelConfig.isThinkingDefault();
    }

    public static int resolveBudget(LlmModelConfig modelConfig, AiLlmConfigService configService) {
        if (modelConfig != null && modelConfig.getThinkingBudgetTokens() != null
                && modelConfig.getThinkingBudgetTokens() > 0) {
            return modelConfig.getThinkingBudgetTokens();
        }
        return configService.getThinkingBudgetTokens();
    }
}
