package com.qualitest.ai.mcp.protocol;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Streamable HTTP 会话注册表。
 * <p>
 * {@code initialize} 时为当前项目分配 UUID 会话 id，供后续 GET SSE 连接校验。
 * 数据仅存于 JVM 内存，适用于单机部署；进程重启后会话失效，客户端需重新 initialize。
 */
@Component
public class McpSessionRegistry {

    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    /**
     * 创建新会话并登记项目 id。
     *
     * @param testProjectId Token 解析出的测试项目 id
     * @return 新生成的会话 id，写入 initialize 响应头 {@link com.qualitest.common.mcp.McpJsonRpc#SESSION_HEADER}
     */
    public String createSession(Long testProjectId) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, testProjectId);
        return sessionId;
    }

    /** 判断会话 id 是否仍有效（未关闭且未过期清理）。 */
    public boolean isValid(String sessionId) {
        return sessionId != null && sessions.containsKey(sessionId);
    }

    /** SSE 连接结束或超时时移除会话，释放内存。 */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
}
