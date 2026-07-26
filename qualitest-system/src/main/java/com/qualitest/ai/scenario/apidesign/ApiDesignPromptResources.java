package com.qualitest.ai.scenario.apidesign;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 加载 AI API 助手的 classpath 文本资源（系统提示词、工具定义、patch JSON Schema）。
 */
public final class ApiDesignPromptResources {

    private ApiDesignPromptResources() {}

    /** 注入模型 system 角色的提示词 */
    public static final String SYSTEM_PROMPT = "ai/api-design-system-prompt.txt";

    /** submit 工具参数的 JSON Schema */
    public static final String PATCH_SCHEMA = "ai/api-design-patch-schema.json";

    /** OpenAI function tools 列表定义 */
    public static final String TOOLS_DEFINITION = "ai/api-design-tools.json";

    /** 按 classpath 路径读取 UTF-8 文本 */
    public static String loadText(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
