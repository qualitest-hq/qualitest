package com.qualitest.ai.mcp.protocol;

/**
 * 一次 MCP HTTP 请求带入分发器的头信息。
 * 会话模式和绑定的测试流只在 initialize 时读取。
 * 之后的 tools/list、tools/call 只凭 Mcp-Session-Id 找回已经登记的会话，不再看这两个头。
 *
 * @param sessionId   已有会话 id，可空
 * @param sessionMode 会话模式，取值 call 或 turn，可空
 * @param testFlowId  回合会话要绑定的测试流 id，可空
 */
public record McpInbound(String sessionId, String sessionMode, String testFlowId) {

    /** 没有会话头的请求：按逐次调用处理，也不绑定测试流。 */
    public static McpInbound empty() {
        return new McpInbound(null, null, null);
    }
}
