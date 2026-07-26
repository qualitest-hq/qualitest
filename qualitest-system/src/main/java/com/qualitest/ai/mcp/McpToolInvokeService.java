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
 * MCP 工具调用编排（单次 {@code tools/call}，无多轮 Agent 循环）。
 * <p>
 * 职责：
 * <ol>
 *   <li>拒绝 {@code submit_flow_design_patch} 及未在 {@link FlowDesignToolNames} 中标记 mcpAllowed 的工具</li>
 *   <li>从 arguments 拆分信封字段（testProjectId、testFlowId、graphJson 等）与业务参数</li>
 *   <li>委托 {@link FlowDesignToolExecutor} 执行，若 resultJson 含顶层 {@code error} 则 {@link com.qualitest.api.result.McpToolResult#error}=true</li>
 * </ol>
 * MCP 典型勘察流：list_flows → 信封带 testFlowId → get_graph_summary / get_subflow_detail（多数场景无需 get_flow 传完整 graphJson）。
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
     * @return 工具输出的 JSON 文本（截断等信息保留在 JSON 内嵌字段中）
     * @throws ServiceException 工具不允许经 MCP 调用，或项目 id 与 Token 不匹配（由上下文工厂抛出）
     */
    public McpToolResult invoke(String toolName, McpToolInvokeParams params, Long tokenProjectId) {
        if (FlowDesignToolNames.SUBMIT_FLOW_DESIGN_PATCH.getId().equals(toolName)) {
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
