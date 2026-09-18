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
 * MCP JSON-RPC 请求分发。
 * <p>
 * 解析 POST 体后按 method 路由：
 * initialize（握手，serverInfo 含项目 id 与是否开启 MCP 全自动写流）；
 * tools/list（按项目开关返回只读或只读+写工具定义）；
 * tools/call（执行单个工具）；
 * ping / notifications。
 * 工具白名单与写库规则由工具调用服务处理。
 */
@Service
@RequiredArgsConstructor
public class McpJsonRpcDispatcher {

    /** 提供 tools/list 用的工具 Schema */
    private final FlowDesignToolsDefinitionService toolsDefinitionService;
    /** 执行 tools/call 与读取项目 MCP 全自动开关 */
    private final McpToolInvokeService mcpToolInvokeService;
    /** 把 MCP arguments 映射为内部调用参数 */
    private final McpToolArgumentsMapper argumentsMapper;
    /** 维护 MCP SSE 会话 */
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
            case "tools/list" -> DispatchResult.response(handleToolsList(id, testProjectId));
            case "tools/call" -> DispatchResult.response(handleToolsCall(id, request, testProjectId));
            case "ping" -> DispatchResult.response(McpJsonRpc.result(id, Map.of()));
            default -> DispatchResult.response(McpJsonRpc.error(id, -32601, "Method not found: " + method));
        };
    }

    /**
     * 握手 initialize：返回协议版本、服务能力，以及项目 id、写流开关、导入接口开关。
     * version 会按已开开关追加后缀（+autopilot / +importApis），便于客户端感知权限变化并刷新工具列表。
     */
    private String handleInitialize(Object id, Long testProjectId) {
        McpToolInvokeService.McpProjectGates gates = mcpToolInvokeService.resolveMcpGates(testProjectId);
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", McpJsonRpc.SERVER_NAME);
        // 开关变化时改 version，避免客户端按旧 version 缓存工具列表
        String version = McpJsonRpc.SERVER_VERSION;
        if (gates.autopilotEnabled()) {
            version = version + "+autopilot";
        }
        if (gates.importApisEnabled()) {
            version = version + "+importApis";
        }
        serverInfo.put("version", version);
        serverInfo.put("testProjectId", String.valueOf(testProjectId));
        serverInfo.put("mcpAutopilotEnabled", gates.autopilotEnabled());
        serverInfo.put("mcpImportApisEnabled", gates.importApisEnabled());

        Map<String, Object> toolsCapability = new LinkedHashMap<>();
        toolsCapability.put("listChanged", true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", McpJsonRpc.PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", toolsCapability));
        result.put("serverInfo", serverInfo);
        return McpJsonRpc.result(id, result);
    }

    /**
     * tools/list：按项目「写流」「导入接口」两个开关组装当前可见工具列表。
     */
    private String handleToolsList(Object id, Long testProjectId) {
        McpToolInvokeService.McpProjectGates gates = mcpToolInvokeService.resolveMcpGates(testProjectId);
        List<Map<String, Object>> mcpTools =
                toolsDefinitionService.loadMcpProtocolTools(gates.autopilotEnabled(), gates.importApisEnabled());
        return McpJsonRpc.result(id, Map.of("tools", mcpTools));
    }

    /** 执行单个工具：解析参数、调用业务、封装 content 文本与 isError */
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
     * 分发结果：响应体、新建的 SSE 会话 id、是否为客户端通知（通知时 HTTP 空体）。
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
