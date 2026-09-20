package com.qualitest.ai.mcp.protocol;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.mcp.McpPromptResourceService;
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
 * 解析 POST 体后按 method 路由到握手、工具列表/调用、规程 Prompt、规程 Resource、ping 与通知处理。
 * initialize 声明提供 tools / prompts / resources，但不声明工具列表变更推送；
 * 项目开关变化后须客户端重连，再通过 tools/list 拉取当前工具集。
 */
@Service
@RequiredArgsConstructor
public class McpJsonRpcDispatcher {

    /** 组装 tools/list 返回的工具 Schema */
    private final FlowDesignToolsDefinitionService toolsDefinitionService;
    /** 执行 tools/call，并读取项目写流 / 导入接口开关 */
    private final McpToolInvokeService mcpToolInvokeService;
    /** 将 MCP arguments 转成内部调用参数 */
    private final McpToolArgumentsMapper argumentsMapper;
    /** 创建与登记 MCP SSE 会话 */
    private final McpSessionRegistry sessionRegistry;
    /** 组装 prompts / resources 载荷，计算合成规程版本 */
    private final McpPromptResourceService mcpPromptResourceService;

    /**
     * 解析并分发一条 JSON-RPC 请求（无操作者，仅适合不触发写流/导入的调用）。
     *
     * @param body          POST 原始 JSON 文本
     * @param testProjectId 当前请求 Token 绑定的测试项目 id
     * @return 响应 JSON、可选新建会话 id、是否为无响应体的通知
     */
    public DispatchResult dispatch(String body, Long testProjectId) {
        return dispatch(body, testProjectId, null);
    }

    /**
     * 解析并分发一条 JSON-RPC 请求。
     * tools/call 会把操作者用户 id 传给工具编排，供写流与导入落审计人。
     *
     * @param body           POST 原始 JSON 文本
     * @param testProjectId  当前请求 Token 绑定的测试项目 id
     * @param operatorUserId Token 绑定的操作者用户 id，可空
     * @return 响应 JSON、可选新建会话 id、是否为无响应体的通知
     */
    public DispatchResult dispatch(String body, Long testProjectId, Long operatorUserId) {
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
            case "tools/call" -> DispatchResult.response(
                    handleToolsCall(id, request, testProjectId, operatorUserId));
            case "prompts/list" -> DispatchResult.response(handlePromptsList(id));
            case "prompts/get" -> DispatchResult.response(handlePromptsGet(id, request, testProjectId));
            case "resources/list" -> DispatchResult.response(handleResourcesList(id));
            case "resources/read" -> DispatchResult.response(handleResourcesRead(id, request, testProjectId));
            case "ping" -> DispatchResult.response(McpJsonRpc.result(id, Map.of()));
            default -> DispatchResult.response(McpJsonRpc.error(id, -32601, "Method not found: " + method));
        };
    }

    /**
     * 处理 initialize：返回协议版本、能力声明与 serverInfo。
     * <p>
     * 能力声明含 tools、prompts、resources（可列工具、取规程 Prompt、读规程 Resource）。
     * tools 不声明 listChanged：服务端不主动推送工具列表变更；
     * 项目写流或导入开关变更后，须客户端重连或刷新 MCP，再调 tools/list 才能看到新工具集。
     * <p>
     * serverInfo 含：服务名、协议 version（可带 +autowrite / +autorun / +importApis）、项目 id、
     * 三道开关布尔值、合成 guideVersion（规程指纹 + 同款门控后缀，供本地 Skill 过期判定）。
     *
     * @param id            请求 id
     * @param testProjectId 测试项目 id
     * @return JSON-RPC 成功响应字符串
     */
    private String handleInitialize(Object id, Long testProjectId) {
        McpToolInvokeService.McpProjectGates gates = mcpToolInvokeService.resolveMcpGates(testProjectId);
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", McpJsonRpc.SERVER_NAME);
        // 按当前项目开关给协议 version 加门控后缀
        serverInfo.put("version", McpPromptResourceService.appendGateSuffixes(
                McpJsonRpc.SERVER_VERSION, gates));
        serverInfo.put("testProjectId", String.valueOf(testProjectId));
        serverInfo.put("mcpAutoWriteEnabled", gates.autoWriteEnabled());
        serverInfo.put("mcpAutorunEnabled", gates.autorunEnabled());
        serverInfo.put("mcpImportApisEnabled", gates.importApisEnabled());
        serverInfo.put("guideVersion", mcpPromptResourceService.guideVersion(gates));

        // 声明三类能力；tools 不带 listChanged
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of());
        capabilities.put("prompts", Map.of());
        capabilities.put("resources", Map.of());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", McpJsonRpc.PROTOCOL_VERSION);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        return McpJsonRpc.result(id, result);
    }

    /**
     * 处理 tools/list：按当前项目「允许 MCP 自动写流」「允许 MCP 自动跑流」「允许 MCP 导入接口」开关，
     * 组装此刻可见的工具定义（关写流则无 submit_* 等；关跑流则无 run_test_flow；关导入则无 import_apis）。
     * 开关在库中已变但客户端未重连时，仍可能继续请求到旧缓存列表，须重连后再调本方法。
     *
     * @param id            请求 id
     * @param testProjectId 测试项目 id
     * @return JSON-RPC 成功响应字符串
     */
    private String handleToolsList(Object id, Long testProjectId) {
        McpToolInvokeService.McpProjectGates gates = mcpToolInvokeService.resolveMcpGates(testProjectId);
        List<Map<String, Object>> mcpTools = toolsDefinitionService.loadMcpProtocolTools(gates);
        return McpJsonRpc.result(id, Map.of("tools", mcpTools));
    }

    /**
     * 处理 tools/call：解析工具名与参数，带上操作者执行业务，封装 content 文本与 isError。
     *
     * @param id             请求 id
     * @param request        完整 JSON-RPC 请求
     * @param testProjectId  测试项目 id
     * @param operatorUserId Token 绑定的操作者用户 id，可空
     * @return JSON-RPC 成功或业务错误响应字符串
     */
    @SuppressWarnings("unchecked")
    private String handleToolsCall(Object id, JSONObject request, Long testProjectId, Long operatorUserId) {
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
            McpToolResult toolResult =
                    mcpToolInvokeService.invoke(toolName, invokeParams, testProjectId, operatorUserId);
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
     * 处理 prompts/list：返回造流相关 Prompt 名称与说明。
     *
     * @param id 请求 id
     * @return JSON-RPC 成功响应字符串
     */
    private String handlePromptsList(Object id) {
        return McpJsonRpc.result(id, Map.of("prompts", mcpPromptResourceService.listPrompts()));
    }

    /**
     * 处理 prompts/get：按 Prompt 名与当前项目三道开关返回已裁剪正文；未知名返回 -32602。
     *
     * @param id            请求 id
     * @param request       完整 JSON-RPC 请求
     * @param testProjectId 测试项目 id
     * @return JSON-RPC 响应字符串
     */
    private String handlePromptsGet(Object id, JSONObject request, Long testProjectId) {
        JSONObject params = request.getJSONObject("params");
        if (params == null) {
            return McpJsonRpc.error(id, -32602, "Invalid params");
        }
        String name = params.getString("name");
        try {
            McpToolInvokeService.McpProjectGates gates = mcpToolInvokeService.resolveMcpGates(testProjectId);
            return McpJsonRpc.result(id, mcpPromptResourceService.getPrompt(name, gates));
        } catch (ServiceException ex) {
            return McpJsonRpc.error(id, -32602, ex.getMessage());
        }
    }

    /**
     * 处理 resources/list：返回造流规程 Resource 摘要。
     *
     * @param id 请求 id
     * @return JSON-RPC 成功响应字符串
     */
    private String handleResourcesList(Object id) {
        return McpJsonRpc.result(id, Map.of("resources", mcpPromptResourceService.listResources()));
    }

    /**
     * 处理 resources/read：按 uri 与当前项目三道开关返回已裁剪的造流硬规矩 Markdown；未知 uri 返回 -32602。
     *
     * @param id            请求 id
     * @param request       完整 JSON-RPC 请求
     * @param testProjectId 测试项目 id
     * @return JSON-RPC 响应字符串
     */
    private String handleResourcesRead(Object id, JSONObject request, Long testProjectId) {
        JSONObject params = request.getJSONObject("params");
        if (params == null) {
            return McpJsonRpc.error(id, -32602, "Invalid params");
        }
        String uri = params.getString("uri");
        try {
            McpToolInvokeService.McpProjectGates gates = mcpToolInvokeService.resolveMcpGates(testProjectId);
            return McpJsonRpc.result(id, mcpPromptResourceService.readResource(uri, gates));
        } catch (ServiceException ex) {
            return McpJsonRpc.error(id, -32602, ex.getMessage());
        }
    }

    /**
     * 单次分发结果：HTTP 响应体、可选新建的 SSE 会话 id、是否为客户端通知（通知时无响应体）。
     */
    @Getter
    public static class DispatchResult {
        /** JSON-RPC 响应体；通知时为 null */
        private final String responseBody;
        /** 握手新建的会话 id；非握手为 null */
        private final String sessionId;
        /** 是否为 notifications/* 且无 id（无响应体） */
        private final boolean notification;

        private DispatchResult(String responseBody, String sessionId, boolean notification) {
            this.responseBody = responseBody;
            this.sessionId = sessionId;
            this.notification = notification;
        }

        /**
         * 普通响应（无新建会话）。
         *
         * @param responseBody JSON 响应文本
         * @return 分发结果
         */
        public static DispatchResult response(String responseBody) {
            return new DispatchResult(responseBody, null, false);
        }

        /**
         * 握手成功响应，并附带新建会话 id。
         *
         * @param responseBody JSON 响应文本
         * @param sessionId    会话 id
         * @return 分发结果
         */
        public static DispatchResult withSession(String responseBody, String sessionId) {
            return new DispatchResult(responseBody, sessionId, false);
        }

        /**
         * 客户端通知：无 HTTP 响应体。
         *
         * @return 分发结果
         */
        public static DispatchResult notification() {
            return new DispatchResult(null, null, true);
        }
    }
}
