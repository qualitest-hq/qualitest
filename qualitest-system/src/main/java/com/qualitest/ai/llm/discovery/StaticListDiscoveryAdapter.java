package com.qualitest.ai.llm.discovery;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 静态模型列表发现：读取模板 staticModels 字段，不访问网络。
 */
@Component
public class StaticListDiscoveryAdapter implements ModelDiscoveryAdapter {

    @Override
    public String discoveryType() {
        return "static_list";
    }

    /**
     * 将模板中预置的 modelId 列表转为 DiscoveredModel；无 staticModels 时返回空。
     */
    @Override
    public List<DiscoveredModel> discover(ModelDiscoveryContext context) {
        if (context.getTemplate() == null || context.getTemplate().getStaticModels() == null) {
            return Collections.emptyList();
        }
        List<DiscoveredModel> models = new ArrayList<>();
        for (String modelId : context.getTemplate().getStaticModels()) {
            if (modelId == null || modelId.isBlank()) {
                continue;
            }
            if (!ModelFilterUtils.matches(modelId, context.getTemplate())) {
                continue;
            }
            models.add(DiscoveredModel.builder()
                    .modelId(modelId)
                    .displayName(modelId)
                    .build());
        }
        return models;
    }
}
