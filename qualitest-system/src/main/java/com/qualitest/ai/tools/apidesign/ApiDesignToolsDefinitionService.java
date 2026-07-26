package com.qualitest.ai.tools.apidesign;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.scenario.apidesign.ApiDesignPromptResources;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 加载 AI API 助手的 OpenAI function 定义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDesignToolsDefinitionService {

    private final ApiDesignToolExecutor apiDesignToolExecutor;

    @Getter
    private volatile List<Map<String, Object>> cachedTools;

    /** 启动时核对工具名集合是否完整且可加载。 */
    @PostConstruct
    void validateToolRegistryConsistency() {
        try {
            validateInternal();
        } catch (IOException e) {
            throw new IllegalStateException("加载 api-design-tools 定义失败", e);
        }
    }

    /** 比对枚举、执行器注册表与 tools 定义中的工具名集合。 */
    private void validateInternal() throws IOException {
        Set<String> declared = ApiDesignToolExecutor.allDeclaredToolNames();
        Set<String> registered = apiDesignToolExecutor.registeredToolNames();
        if (!declared.equals(registered)) {
            throw new IllegalStateException("ApiDesignToolNames 与 ApiDesignToolExecutor 注册不一致");
        }
        Set<String> fromJson = new HashSet<>(extractToolNames(loadToolsDefinitionRaw()));
        if (!fromJson.equals(declared)) {
            throw new IllegalStateException("api-design-tools.json 与 ApiDesignToolNames 不一致");
        }
        log.info("API Design 工具注册校验通过: {}", fromJson.size());
    }

    /** 加载并缓存 OpenAI function 定义；submit 工具的 parameters 注入 patch JSON Schema。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> loadToolsDefinition() {
        List<Map<String, Object>> local = cachedTools;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedTools != null) {
                return cachedTools;
            }
            try {
                String json = ApiDesignPromptResources.loadText(ApiDesignPromptResources.TOOLS_DEFINITION);
                String schemaJson = ApiDesignPromptResources.loadText(ApiDesignPromptResources.PATCH_SCHEMA);
                JSONObject patchSchema = JSON.parseObject(schemaJson);
                JSONArray arr = JSON.parseArray(json);
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject tool = arr.getJSONObject(i);
                    JSONObject fn = tool.getJSONObject("function");
                    if (fn != null && ApiDesignToolNames.SUBMIT_API_DESIGN_PATCH.getId().equals(fn.getString("name"))) {
                        fn.put("parameters", patchSchema);
                    }
                }
                cachedTools = arr.stream()
                        .map(item -> (Map<String, Object>) JSON.parseObject(JSON.toJSONString(item)))
                        .toList();
                return cachedTools;
            } catch (IOException e) {
                throw new LlmClientException("加载 API 设计 AI tools 定义失败", e);
            }
        }
    }

    /** 从 classpath 读取 tools 定义原文，不做 patch schema 注入。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadToolsDefinitionRaw() throws IOException {
        String json = ApiDesignPromptResources.loadText(ApiDesignPromptResources.TOOLS_DEFINITION);
        JSONArray arr = JSON.parseArray(json);
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            list.add((Map<String, Object>) JSON.parseObject(JSON.toJSONString(arr.getJSONObject(i))));
        }
        return list;
    }

    /** 从 function 定义列表提取工具 name 字段。 */
    private static List<String> extractToolNames(List<Map<String, Object>> tools) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            Object fn = tool.get("function");
            if (fn instanceof Map<?, ?> fnMap) {
                Object name = fnMap.get("name");
                if (name != null) {
                    names.add(String.valueOf(name));
                }
            }
        }
        return names;
    }
}
