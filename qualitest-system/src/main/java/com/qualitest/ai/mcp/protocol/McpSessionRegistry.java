package com.qualitest.ai.mcp.protocol;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Streamable HTTP 会话登记。
 * <p>
 * 会话 id 在 initialize 时签发，供后续请求头 {@code Mcp-Session-Id} 与 SSE 下行校验。
 * 移除会话只丢掉内存中的工作图与尚未跑流的单元，不另行写入测试流。
 */
@Component
public class McpSessionRegistry {

    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

    /**
     * 登记一条逐次调用会话。
     *
     * @param testProjectId 项目 id
     * @return 新建会话
     */
    public McpSession createSession(Long testProjectId) {
        return createSession(testProjectId, McpSession.Mode.CALL, null);
    }

    /**
     * 登记一条会话。回合会话须已绑定测试流。
     *
     * @param testProjectId 项目 id
     * @param mode          会话模式
     * @param testFlowId    回合会话绑定的测试流，逐次调用可为 null
     * @return 新建会话
     */
    public McpSession createSession(Long testProjectId, McpSession.Mode mode, Long testFlowId) {
        String sessionId = UUID.randomUUID().toString();
        McpSession session = new McpSession(sessionId, testProjectId, mode, testFlowId);
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * 按会话 id 取会话。
     *
     * @param sessionId 请求头中的会话 id
     * @return 会话；不存在时 null
     */
    public McpSession get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessions.get(sessionId);
    }

    /**
     * 判断会话 id 是否仍有效。
     *
     * @param sessionId 请求头中的会话 id
     * @return true 表示已登记且未移除
     */
    public boolean isValid(String sessionId) {
        return get(sessionId) != null;
    }

    /**
     * 移除会话。内存中尚未跑流的单元一并丢弃。
     *
     * @param sessionId 会话 id
     */
    public void remove(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
}
