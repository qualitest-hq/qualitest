package com.qualitest.ai.llm.discovery;

import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.llm.lc4j.Lc4jClientFactory;
import dev.langchain4j.model.catalog.ModelDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容协议的模型发现适配器。
 * <p>
 * discoveryType = openai_models。
 * 用厂商 baseUrl + apiKey 拉取上游模型列表，再按厂商模板的 include/exclude 规则过滤，
 * 供管理端「发现模型」「测连」使用。
 */
@Component
@RequiredArgsConstructor
public class OpenAiModelsDiscoveryAdapter implements ModelDiscoveryAdapter {

    private final Lc4jClientFactory lc4jClientFactory;

    @Override
    public String discoveryType() {
        return "openai_models";
    }

    /**
     * 请求上游模型列表并过滤。
     * 缺 API Key 直接失败；网络或解析异常包装为业务异常。
     *
     * @param context 含 baseUrl、apiKey、超时与厂商模板过滤条件
     * @return 通过过滤的模型 id / 展示名 / 归属方列表
     */
    @Override
    public List<DiscoveredModel> discover(ModelDiscoveryContext context) {
        if (context.getApiKey() == null || context.getApiKey().isBlank()) {
            throw new LlmClientException("API Key 未配置");
        }
        try {
            List<ModelDescription> listed = lc4jClientFactory
                    .openAiModelCatalog(
                            context.getBaseUrl(),
                            context.getApiKey(),
                            context.getConnectTimeoutMs() > 0 ? context.getConnectTimeoutMs() : 5000,
                            context.getReadTimeoutMs() > 0 ? context.getReadTimeoutMs() : 15000)
                    .listModels();
            List<DiscoveredModel> models = new ArrayList<>();
            if (listed == null) {
                return models;
            }
            for (ModelDescription item : listed) {
                if (item == null || item.name() == null || item.name().isBlank()) {
                    continue;
                }
                String modelId = item.name();
                if (!ModelFilterUtils.matches(modelId, context.getTemplate())) {
                    continue;
                }
                models.add(DiscoveredModel.builder()
                        .modelId(modelId)
                        .displayName(item.displayName() != null ? item.displayName() : modelId)
                        .ownedBy(item.owner())
                        .build());
            }
            return models;
        } catch (LlmClientException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new LlmClientException("拉取模型列表失败: " + ex.getMessage(), ex);
        }
    }
}
