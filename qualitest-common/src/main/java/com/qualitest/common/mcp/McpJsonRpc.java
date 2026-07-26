package com.qualitest.common.mcp;

import com.alibaba.fastjson2.JSON;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP JSON-RPC 信封构建与鉴权错误响应。
 * <p>
 * 供 {@code /api/project/mcp} 协议分发与 Project Token 过滤器共用，统一 jsonrpc 版本号与错误码。
 */
public final class McpJsonRpc {

    /** JSON-RPC 协议版本字段值。 */
    public static final String JSONRPC_VERSION = "2.0";

    /** initialize 响应中的 MCP 协议版本声明。 */
    public static final String PROTOCOL_VERSION = "2024-11-05";

    /** initialize 响应 serverInfo.name。 */
    public static final String SERVER_NAME = "qualitest";

    /** initialize 响应 serverInfo.version。 */
    public static final String SERVER_VERSION = "1.0.0";

    /** Streamable HTTP 会话响应/请求头名。 */
    public static final String SESSION_HEADER = "Mcp-Session-Id";

    /** Project Token 鉴权失败时 JSON-RPC error.code。 */
    public static final int AUTH_ERROR_CODE = -32001;

    private McpJsonRpc() {
    }

    /**
     * 构造 JSON-RPC 成功响应 JSON 字符串。
     *
     * @param id     请求 id，可为 null
     * @param result result 对象
     */
    public static String result(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.put("id", id);
        response.put("result", result);
        return JSON.toJSONString(response);
    }

    /**
     * 构造 JSON-RPC 错误响应 JSON 字符串。
     *
     * @param id      请求 id，可为 null
     * @param code    错误码
     * @param message 错误说明
     */
    public static String error(Object id, int code, String message) {
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("code", code);
        errorBody.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.put("id", id);
        response.put("error", errorBody);
        return JSON.toJSONString(response);
    }

    /**
     * 构造 MCP 端点鉴权失败时的 JSON-RPC 401 响应体（id 固定为 null）。
     */
    public static String authErrorJson(String message) {
        return error(null, AUTH_ERROR_CODE, message);
    }

    /** 对写入 JSON 字符串的 message 做最小转义。 */
    public static String escapeJson(String message) {
        if (message == null) {
            return "";
        }
        return message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
