package com.qualitest.ai.llm.discovery;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.Request;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Ollama 本地模型发现：GET /api/tags，解析 models[].name。
 */
@Component
public class OllamaTagsDiscoveryAdapter implements ModelDiscoveryAdapter {

    @Override
    public String discoveryType() {
        return "ollama_tags";
    }

    /**
     * 请求 Ollama tags 接口；apiKey 非空时附加 Bearer 头。
     */
    @Override
    public List<DiscoveredModel> discover(ModelDiscoveryContext context) {
        String url = LlmDiscoveryUrlUtils.buildOllamaTagsUrl(context.getBaseUrl());
        Request.Builder builder = new Request.Builder();
        if (context.getApiKey() != null && !context.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + context.getApiKey());
        }
        String body = DiscoveryHttpClient.get(url, context, builder);
        JSONObject json = JSONObject.parseObject(body);
        JSONArray models = json.getJSONArray("models");
        List<DiscoveredModel> result = new ArrayList<>();
        if (models != null) {
            for (int i = 0; i < models.size(); i++) {
                JSONObject item = models.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String modelId = item.getString("name");
                if (modelId == null || modelId.isBlank()) {
                    continue;
                }
                if (!ModelFilterUtils.matches(modelId, context.getTemplate())) {
                    continue;
                }
                result.add(DiscoveredModel.builder()
                        .modelId(modelId)
                        .displayName(modelId)
                        .build());
            }
        }
        return result;
    }
}
