package com.qualitest.ai.llm.discovery;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.llm.LlmClientException;
import okhttp3.Request;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容协议模型发现：GET /v1/models，按模板 modelFilters 过滤。
 */
@Component
public class OpenAiModelsDiscoveryAdapter implements ModelDiscoveryAdapter {

    @Override
    public String discoveryType() {
        return "openai_models";
    }

    /**
     * 请求上游模型列表，解析 data[].id 并应用 include/exclude 过滤。
     */
    @Override
    public List<DiscoveredModel> discover(ModelDiscoveryContext context) {
        if (context.getApiKey() == null || context.getApiKey().isBlank()) {
            throw new LlmClientException("API Key 未配置");
        }
        String url = LlmDiscoveryUrlUtils.buildOpenAiModelsUrl(context.getBaseUrl());
        String body = DiscoveryHttpClient.get(url, context, new Request.Builder()
                .header("Authorization", "Bearer " + context.getApiKey()));
        JSONObject json = JSONObject.parseObject(body);
        JSONArray data = json.getJSONArray("data");
        List<DiscoveredModel> models = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String modelId = item.getString("id");
                if (modelId == null || modelId.isBlank()) {
                    continue;
                }
                if (!ModelFilterUtils.matches(modelId, context.getTemplate())) {
                    continue;
                }
                models.add(DiscoveredModel.builder()
                        .modelId(modelId)
                        .displayName(modelId)
                        .ownedBy(item.getString("owned_by"))
                        .build());
            }
        }
        return models;
    }
}
