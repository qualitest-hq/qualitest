package com.qualitest.ai.llm.template;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.qualitest.ai.result.ProviderTemplateResult;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内置厂商模板注册表：启动时从 classpath 加载 provider-templates.json。
 */
@Component
public class ProviderTemplateRegistry {

    /** templateId → 模板定义 */
    private Map<String, ProviderTemplate> templatesById = Collections.emptyMap();

    /**
     * 启动加载 JSON 并构建不可变索引。
     */
    @PostConstruct
    public void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("ai/llm/provider-templates.json");
        try (InputStream in = resource.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<ProviderTemplate> list = JSON.parseArray(json, ProviderTemplate.class);
            Map<String, ProviderTemplate> map = new LinkedHashMap<>();
            if (list != null) {
                for (ProviderTemplate template : list) {
                    if (template.getTemplateId() != null) {
                        map.put(template.getTemplateId(), template);
                    }
                }
            }
            this.templatesById = Collections.unmodifiableMap(map);
        }
    }

    /** 按模板 ID 查询；不存在返回 null */
    public ProviderTemplate getById(String templateId) {
        if (templateId == null) {
            return null;
        }
        return templatesById.get(templateId);
    }

    /** 返回全部模板的管理端 DTO 列表 */
    public List<ProviderTemplateResult> listAll() {
        return templatesById.values().stream()
                .map(this::toResult)
                .collect(Collectors.toList());
    }

    /** 将领域模板转为 API 响应对象（不含 modelFilters 等内部字段） */
    public ProviderTemplateResult toResult(ProviderTemplate template) {
        if (template == null) {
            return null;
        }
        return ProviderTemplateResult.builder()
                .templateId(template.getTemplateId())
                .displayName(template.getDisplayName())
                .icon(template.getIcon())
                .provider(template.getProvider())
                .defaultBaseUrl(template.getDefaultBaseUrl())
                .baseUrlEditable(template.getBaseUrlEditable())
                .discoveryType(template.getDiscoveryType())
                .discoveryPath(template.getDiscoveryPath())
                .recommendedModels(template.getRecommendedModels())
                .docUrl(template.getDocUrl())
                .build();
    }
}
