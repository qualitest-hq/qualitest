package com.qualitest.ai.mcp;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolContextFactory;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.api.result.McpToolResult;
import com.qualitest.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * MCP 工具调用编排（单次 tools/call，无多轮 Agent）。
 * <p>
 * 一律拒绝 submit_*（改图只能走 Web 面板 Staging）；再按 mcpAllowed 过滤其它工具。
 * 从 arguments 拆信封（项目/流/画布）与业务参数后交给执行器；结果含顶层 error 时标记失败。
 */
@Service
@RequiredArgsConstructor
public class McpToolInvokeService {

    private final FlowDesignToolExecutor flowDesignToolExecutor;
    private final FlowDesignToolContextFactory contextFactory;

    /**
     * 执行一次 MCP 只读工具。
     *
     * @param toolName       tools/call 中的工具名
     * @param params         信封字段与业务 arguments
     * @param tokenProjectId 请求头 Token 解析出的项目 id；用于默认项目与越权校验
     * @return 工具输出的 JSON 文本
     * @throws ServiceException 工具不允许经 MCP 调用，或项目与 Token 不匹配
     */
    public McpToolResult invoke(String toolName, McpToolInvokeParams params, Long tokenProjectId) {
        // 画布写工具禁止走 MCP，避免绕过面板确认
        if (FlowDesignToolNames.isSubmitUnitTool(toolName)) {
            throw new ServiceException("MCP 不支持修改测试流；请在质衡 Web 端 AI 助手面板提交并合并 patch");
        }
        if (!FlowDesignToolExecutor.isMcpAllowedTool(toolName)) {
            throw new ServiceException("MCP 不支持的工具: " + toolName);
        }
        FlowDesignToolContext context = contextFactory.fromMcpRequest(params, tokenProjectId);
        String resultJson = flowDesignToolExecutor.executeTool(toolName, params.toArgumentsJson(), context);
        return McpToolResult.builder()
                .tool(toolName)
                .resultJson(resultJson)
                .error(FlowDesignToolSupport.isErrorResult(resultJson))
                .build();
    }
}
