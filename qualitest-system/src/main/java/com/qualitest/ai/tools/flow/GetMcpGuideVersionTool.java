package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.mcp.McpPromptResourceService;
import com.qualitest.ai.mcp.McpToolInvokeService.McpProjectGates;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.QualitestTool;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 只读工具：查询当前项目的合成规程版本（源文指纹 + 可选写流/跑流/导入后缀）及使用提示。
 */
@RequiredArgsConstructor
public class GetMcpGuideVersionTool implements QualitestTool {

    /** 规程文本加载与版本串计算 */
    private final McpPromptResourceService mcpPromptResourceService;
    /** 按项目 id 读取写流 / 跑流 / 导入开关 */
    private final TestProjectMapper testProjectMapper;

    /**
     * 返回工具名。
     *
     * @return get_mcp_guide_version
     */
    @Override
    public String getName() {
        return FlowDesignToolNames.GET_MCP_GUIDE_VERSION.getId();
    }

    /**
     * 执行工具：根据调用上下文中的项目 id 读出三道开关，返回合成 guideVersion 与 hint。
     * 无入参。hint 说明如何与本地 Skill 文件中的版本做字符串全等比较、何时需要重新同步 Skill。
     *
     * @param arguments 工具参数（忽略）
     * @param ctx       调用上下文（取其中的 testProjectId）
     * @return JSON 字符串，字段含 guideVersion、hint
     */
    @Override
    public String execute(Map<String, Object> arguments, FlowDesignToolContext ctx) {
        Long projectId = ctx != null ? ctx.getTestProjectId() : null;
        McpProjectGates gates = McpProjectGates.fromProjectId(testProjectMapper, projectId);
        String version = mcpPromptResourceService.guideVersion(gates);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guideVersion", version);
        body.put("hint", "将返回值与业务仓 .cursor/skills/qualitest/SKILL.md 中的 guideVersion 做字符串全等比较；"
                + "一致则勿再 prompts/get 规程正文。不一致（含改写流/跑流/导入开关导致"
                + " +autowrite/+autorun/+importApis 后缀变化）时执行 qualitest_sync_local_skill。"
                + "格式为 {规程指纹}[+autowrite][+autorun][+importApis]。");
        return JSON.toJSONString(body);
    }
}
