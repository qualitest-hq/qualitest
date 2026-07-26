package com.qualitest.ai.scenario.flow;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 测试流 AI 相关 classpath 资源路径。
 * <p>
 * 资源目录：{@code src/main/resources/ai/}
 */
public final class FlowDesignPromptResources {

    private FlowDesignPromptResources() {}

    /**
     * System 角色提示词。
     * 描述设计助手职责、节点类型、占位符规则、何时调用 submit 与只读工具的策略。
     */
    public static final String SYSTEM_PROMPT = "ai/flow-design-system-prompt.txt";

    /**
     * {@code submit_flow_design_patch} 参数的 JSON Schema。
     * 运行时合并进 Web Agent 的 tools 定义，约束模型提交的 FlowDesignPatch 结构。
     */
    public static final String PATCH_SCHEMA = "ai/flow-design-patch-schema.json";

    /**
     * Web「AI 设计」Agent 的 function 工具清单。
     * 含只读查询工具与 {@code submit_flow_design_patch}。
     */
    public static final String TOOLS_DEFINITION = "ai/flow-design-tools.json";

    /**
     * MCP 网关额外工具清单。
     * 当前包含 {@code list_flows}、{@code get_flow}，供 IDE 侧拉取测试流后再分析画布；
     * 不注入 Web Agent，避免增加无关 tool 描述。
     */
    public static final String MCP_EXTRA_TOOLS = "ai/flow-design-mcp-extra-tools.json";

    /** 从 classpath 加载文本资源 */
    public static String loadText(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
