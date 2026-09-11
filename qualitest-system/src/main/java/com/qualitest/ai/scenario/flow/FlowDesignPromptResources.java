package com.qualitest.ai.scenario.flow;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 测试流 AI 相关 classpath 资源路径（目录 ai/）。
 */
public final class FlowDesignPromptResources {

    private FlowDesignPromptResources() {}

    /**
     * System 角色提示词：助手职责、节点类型、占位符、何时调用 submit_* 与只读工具。
     */
    public static final String SYSTEM_PROMPT = "ai/flow-design-system-prompt.txt";

    /**
     * Web 造流 Agent 的 function 清单：只读查询 + 分类型 submit_* 单元工具参数 Schema。
     */
    public static final String TOOLS_DEFINITION = "ai/flow-design-tools.json";

    /**
     * 仅 MCP 额外工具：list_flows、get_flow（列流/读流）。
     * 不注入 Web Agent。
     */
    public static final String MCP_EXTRA_TOOLS = "ai/flow-design-mcp-extra-tools.json";

    /** 从 classpath 加载文本资源 */
    public static String loadText(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
