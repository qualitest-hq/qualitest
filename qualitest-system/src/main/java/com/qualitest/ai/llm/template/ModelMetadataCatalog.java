package com.qualitest.ai.llm.template;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * 模型元数据目录：启动时加载 classpath 下 ai/llm/model-metadata.json，
 * 按上游 modelId 查询展示名与思考能力等属性。
 * <p>
 * 查找顺序：先精确匹配 modelId；没有则按最长前缀通配（键以 * 结尾，例如 deepseek-v4-*）。
 */
@Component
public class ModelMetadataCatalog {

    /** 上游 modelId（或通配键）到元数据的索引 */
    private Map<String, ModelMetadata> metadataByModelId = Collections.emptyMap();

    /**
     * 启动时读取 JSON 并建成不可变索引。
     *
     * @throws IOException 资源不存在或读取失败
     */
    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("ai/llm/model-metadata.json");
        try (InputStream in = resource.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, ModelMetadata> map = JSON.parseObject(json, new TypeReference<Map<String, ModelMetadata>>() {
            });
            this.metadataByModelId = map == null ? Collections.emptyMap() : Collections.unmodifiableMap(map);
        }
    }

    /**
     * 按上游 modelId 查询元数据。
     *
     * @param modelId 上游模型标识，空白返回 null
     * @return 命中的元数据；精确与通配均无则返回 null
     */
    public ModelMetadata get(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        ModelMetadata exact = metadataByModelId.get(modelId);
        if (exact != null) {
            return exact;
        }
        return matchWildcard(modelId);
    }

    /**
     * 通配匹配：遍历以 * 结尾的键，取能匹配且前缀最长的一条。
     *
     * @param modelId 上游模型标识
     * @return 最佳通配元数据，没有则 null
     */
    private ModelMetadata matchWildcard(String modelId) {
        ModelMetadata best = null;
        int bestPrefixLen = -1;
        for (Map.Entry<String, ModelMetadata> entry : metadataByModelId.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.endsWith("*") || key.length() < 2) {
                continue;
            }
            String prefix = key.substring(0, key.length() - 1);
            if (modelId.startsWith(prefix) && prefix.length() > bestPrefixLen) {
                best = entry.getValue();
                bestPrefixLen = prefix.length();
            }
        }
        return best;
    }
}
