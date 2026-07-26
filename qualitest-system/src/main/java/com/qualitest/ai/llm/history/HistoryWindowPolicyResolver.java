package com.qualitest.ai.llm.history;

import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.llm.LlmModelConfig;
import com.qualitest.ai.llm.template.ModelMetadata;
import com.qualitest.ai.llm.template.ModelMetadataCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 根据全局配置与模型 contextWindow 解析有效历史窗口策略。
 */
@Component
@RequiredArgsConstructor
public class HistoryWindowPolicyResolver {

    /** contextWindow 中分配给历史的最大比例 */
    private static final double HISTORY_WINDOW_RATIO = 0.35;

    /** token 预算安全系数，避免估算偏差导致超窗 */
    private static final double TOKEN_SAFETY_FACTOR = 0.9;

    private static final int DEFAULT_CONTEXT_WINDOW = 32768;

    private final AiLlmConfigService aiLlmConfigService;
    private final ModelMetadataCatalog modelMetadataCatalog;

    public HistoryWindowPolicy resolve(LlmModelConfig modelConfig) {
        int countLimit = aiLlmConfigService.getHistoryCountLimit();
        int configuredBudget = aiLlmConfigService.getHistoryTokenBudget();
        int contextWindow = resolveContextWindow(modelConfig);
        int maxFromWindow = (int) (contextWindow * HISTORY_WINDOW_RATIO);
        int effectiveBudget = (int) (Math.min(configuredBudget, maxFromWindow) * TOKEN_SAFETY_FACTOR);
        return HistoryWindowPolicy.builder()
                .countLimit(countLimit)
                .tokenBudget(Math.max(effectiveBudget, 512))
                .build();
    }

    private int resolveContextWindow(LlmModelConfig modelConfig) {
        if (modelConfig == null || modelConfig.getModelName() == null) {
            return DEFAULT_CONTEXT_WINDOW;
        }
        ModelMetadata metadata = modelMetadataCatalog.get(modelConfig.getModelName());
        if (metadata != null && metadata.getContextWindow() != null && metadata.getContextWindow() > 0) {
            return metadata.getContextWindow();
        }
        return DEFAULT_CONTEXT_WINDOW;
    }
}
