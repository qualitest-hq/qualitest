package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.mcp.McpPromptResourceService;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.QualitestTool;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 只读工具 get_mcp_guide_version：返回当前造流规程版本指纹与使用提示。
 */
@RequiredArgsConstructor
public class GetMcpGuideVersionTool implements QualitestTool {

    /** 规程加载与版本指纹计算 */
    private final McpPromptResourceService mcpPromptResourceService;

    /**
     * 工具名。
     *
     * @return get_mcp_guide_version
     */
    @Override
    public String getName() {
        return FlowDesignToolNames.GET_MCP_GUIDE_VERSION.getId();
    }

    /**
     * 执行工具：写出 guideVersion 与 hint JSON，无入参。
     *
     * @param arguments 工具参数（忽略）
     * @param ctx       调用上下文（本工具不使用）
     * @return JSON 字符串，含 guideVersion、hint
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guideVersion", mcpPromptResourceService.guideVersion());
        body.put("hint", "将返回值与业务仓 .cursor/skills/qualitest/SKILL.md 中的 guideVersion 比较；"
                + "未过期则勿再 prompts/get 规程正文。已过期时执行 qualitest_sync_local_skill。");
        return JSON.toJSONString(body);
    }
}
