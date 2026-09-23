package com.qualitest.ai.llm.discovery;

import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.llm.LlmUpstreamErrorMessages;
import com.qualitest.ai.llm.lc4j.Lc4jClientFactory;
import dev.langchain4j.model.catalog.ModelDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容协议的模型发现适配器（discoveryType = openai_models）。
 * <p>
 * 使用厂商 baseUrl 与 apiKey 请求上游模型列表，再按厂商模板的 include / exclude 规则过滤，
 * 结果供管理端「发现模型」「测连」展示与入库。
 */
@Component
@RequiredArgsConstructor
public class OpenAiModelsDiscoveryAdapter implements ModelDiscoveryAdapter {

    /** 构建模型目录客户端（按 baseUrl、apiKey、超时请求上游列表接口）。 */
    private final Lc4jClientFactory lc4jClientFactory;

    /**
     * 本适配器对应的发现类型标识。
     *
     * @return 固定为 openai_models
     */
    @Override
    public String discoveryType() {
        return "openai_models";
    }

    /**
     * 请求上游模型列表并过滤。
     * <ul>
     *   <li>未配置 API Key 时直接失败</li>
     *   <li>连接超时默认 5s、读超时默认 15s（上下文已配置正数时用上下文值）</li>
     *   <li>跳过空名模型；不匹配模板过滤规则的模型丢弃</li>
     *   <li>上游运行时异常转为中文业务异常（鉴权失败等不透出原始 JSON）</li>
     * </ul>
     *
     * @param context 含 baseUrl、apiKey、超时与厂商模板过滤条件
     * @return 通过过滤的模型 id、展示名、归属方列表；上游返回空时为空列表
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
            // 业务异常原样抛出
            throw ex;
        } catch (RuntimeException ex) {
            // 上游原始异常转为用户可读中文说明
            throw new LlmClientException(LlmUpstreamErrorMessages.forDiscovery(ex), ex);
        }
    }
}
