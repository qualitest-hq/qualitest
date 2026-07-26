package com.qualitest.ai.mcp.protocol;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.mcp.McpToolInvokeService;
import com.qualitest.ai.tools.FlowDesignToolsDefinitionService;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.api.result.McpToolResult;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.mcp.McpJsonRpc;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP JSON-RPC 请求分发器。
 * <p>
 * 将 POST 请求体解析为 JSON-RPC，按 {@code method} 路由到具体处理逻辑，并序列化为 JSON-RPC 响应字符串。
 * 支持的方法：
 * <ul>
 *   <li>{@code initialize}：返回协议版本、能力与 serverInfo（含 testProjectId），并创建 SSE 会话</li>
 *   <li>{@code tools/list}：返回当前项目可用的只读工具定义列表</li>
 *   <li>{@code tools/call}：执行单个工具，结果封装为 MCP content 文本</li>
 *   <li>{@code ping}：空 result 心跳</li>
 *   <li>{@code notifications/*}：客户端通知，HTTP 层返回空响应体</li>
 * </ul>
 * 工具执行委托 {@link McpToolInvokeService}，不在此重复白名单与业务规则。
 */
@Service
@RequiredArgsConstructor
public class McpJsonRpcDispatcher {

    private final FlowDesignToolsDefinitionService toolsDefinitionService;
    private final McpToolInvokeService mcpToolInvokeService;
    private final McpToolArgumentsMapper argumentsMapper;
    private final McpSessionRegistry sessionRegistry;

    /**
     * 解析并分发一条 JSON-RPC 请求。
     *
     * @param body          POST 原始 JSON 文本
     * @param testProjectId 当前请求 Token 绑定的测试项目 id
     * @return 分发结果：响应 JSON 字符串、可选会话 id、是否为无响应体的通知
     */
    public DispatchResult dispatch(String body, Long testProjectId) {
        JSONObject request;
        try {
            request = JSON.parseObject(body);
        } catch (Exception ex) {
            return DispatchResult.response(McpJsonRpc.error(null, -32700, "Parse error"));
        }
        if (request == null) {
            return DispatchResult.response(McpJsonRpc.error(null, -32700, "Parse error"));
        }

        String method = request.getString("method");
        Object id = request.get("id");
        boolean isNotification = id == null && method != null && method.startsWith("notifications/");

        if (isNotification) {
            return DispatchResult.notification();
        }

        if (method == null) {
            return DispatchResult.response(McpJsonRpc.error(id, -32600, "Invalid Request"));
        }

        return switch (method) {
            case "initialize" -> DispatchResult.withSession(
                    handleInitialize(id, testProjectId),
                    sessionRegistry.createSession(testProjectId));
            case "tools/list" -> DispatchResult.response(handleToolsList(id));
            case "tools/call" -> DispatchResult.response(handleToolsCall(id, request, testProjectId));
            case "ping" -> DispatchResult.response(McpJsonRpc.result(id, Map.of()));
            default -> DispatchResult.response(McpJsonRpc.error(id, -32601, "Method not found: " + method));
        };
    }

    private String handleInitialize(Object id, Long testProjectId) {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", McpJsonRpc.SERVER_NAME);
        serverInfo.put("version", McpJsonRpc.SERVER_VERSION);
        serverInfo.put("testProjectId", String.valueOf(testProjectId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", McpJsonRpc.PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", serverInfo);
        return McpJsonRpc.result(id, result);
    }

    private String handleToolsList(Object id) {
        List<Map<String, Object>> mcpTools = toolsDefinitionService.loadMcpProtocolTools();
        return McpJsonRpc.result(id, Map.of("tools", mcpTools));
    }

    @SuppressWarnings("unchecked")
    private String handleToolsCall(Object id, JSONObject request, Long testProjectId) {
        JSONObject params = request.getJSONObject("params");
        if (params == null) {
            return McpJsonRpc.error(id, -32602, "Invalid params");
        }
        String toolName = params.getString("name");
        if (toolName == null || toolName.isBlank()) {
            return McpJsonRpc.error(id, -32602, "Missing tool name");
        }
        Map<String, Object> rawArguments = params.getObject("arguments", Map.class);
        McpToolInvokeParams invokeParams = argumentsMapper.fromToolArguments(rawArguments);
        try {
            McpToolResult toolResult = mcpToolInvokeService.invoke(toolName, invokeParams, testProjectId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", List.of(Map.of(
                    "type", "text",
                    "text", toolResult.getResultJson() != null ? toolResult.getResultJson() : "")));
            result.put("isError", toolResult.isError());
            return McpJsonRpc.result(id, result);
        } catch (ServiceException ex) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", List.of(Map.of("type", "text", "text", ex.getMessage())));
            result.put("isError", true);
            return McpJsonRpc.result(id, result);
        }
    }

    /**
     * {@link #dispatch} 的返回封装。
     */
    @Getter
    public static class DispatchResult {
        private final String responseBody;
        private final String sessionId;
        private final boolean notification;

        private DispatchResult(String responseBody, String sessionId, boolean notification) {
            this.responseBody = responseBody;
            this.sessionId = sessionId;
            this.notification = notification;
        }

        public static DispatchResult response(String responseBody) {
            return new DispatchResult(responseBody, null, false);
        }

        public static DispatchResult withSession(String responseBody, String sessionId) {
            return new DispatchResult(responseBody, sessionId, false);
        }

        public static DispatchResult notification() {
            return new DispatchResult(null, null, true);
        }
    }
}
