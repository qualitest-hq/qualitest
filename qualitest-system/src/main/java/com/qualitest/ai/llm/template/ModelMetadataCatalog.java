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
 * 模型元数据目录：启动时加载 model-metadata.json，按上游 modelId 补全展示名与思考能力。
 */
@Component
public class ModelMetadataCatalog {

    /** 上游 modelId → 元数据 */
    private Map<String, ModelMetadata> metadataByModelId = Collections.emptyMap();

    /**
     * 启动加载 JSON 并构建不可变索引。
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

    /** 按上游 modelId 查询元数据；无记录返回 null */
    public ModelMetadata get(String modelId) {
        if (modelId == null) {
            return null;
        }
        return metadataByModelId.get(modelId);
    }
}
