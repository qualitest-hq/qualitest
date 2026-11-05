package com.qualitest.flow.sync;

import com.qualitest.common.core.redis.RedisCache;
import com.qualitest.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 测试流画布写锁（悲观离线锁）。
 * <p>
 * 按测试流 id 在 Redis 中保存当前持锁端标识，并带 TTL。
 * 同一端可重入并续期；其它端写入立即冲突。
 * <ul>
 *   <li>Web 标签页：token 形如 web:用户名:uuid，靠心跳续期（默认 30 秒）</li>
 *   <li>MCP / Web AI：端 ID 稳定（mcp:会话 或 web-autopilot:对话），默认 60 秒，
 *       该流上的工具调用续期；会话结束或显式释放时删除</li>
 * </ul>
 * 只约束写 graph_json；打开浏览不占锁。
 */
@Service
@RequiredArgsConstructor
public class FlowEditLeaseService {

    /** 保存画布请求头中携带租约 token 的字段名 */
    public static final String HEADER_NAME = "X-Flow-Edit-Lease";
    /** Redis 键前缀，后接测试流 id */
    private static final String KEY_PREFIX = "qualitest:flow:edit-lease:";
    /** Web 草稿租约默认存活秒数；心跳须在过期前调用 */
    public static final int DEFAULT_TTL_SECONDS = 30;
    /** MCP / Web AI 会话锁默认存活秒数 */
    public static final int MCP_TTL_SECONDS = 60;

    private final RedisCache redisCache;

    /**
     * 抢占写锁（Web 标签页）。
     * 成功则写入 Redis 并返回新 token；已被占用则抛冲突异常。
     *
     * @param testFlowId 测试流 id
     * @param holderId   持锁方前缀（如 web:用户名），会拼进 token 便于辨认
     * @return 新租约 token（持锁方前缀 + UUID）
     */
    public String tryAcquire(Long testFlowId, String holderId) {
        return tryAcquire(testFlowId, holderId, DEFAULT_TTL_SECONDS);
    }

    /**
     * 抢占写锁并指定 TTL。
     */
    public String tryAcquire(Long testFlowId, String holderId, int ttlSeconds) {
        if (testFlowId == null) {
            throw new ServiceException("缺少 testFlowId，无法抢写锁");
        }
        String token = (holderId != null && !holderId.isBlank() ? holderId.trim() : "anon")
                + ":" + UUID.randomUUID();
        String key = key(testFlowId);
        int ttl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        if (!redisCache.setIfAbsent(key, token, ttl, TimeUnit.SECONDS)) {
            Object held = redisCache.getCacheObject(key);
            throw new FlowEditLeaseConflictException(held != null ? String.valueOf(held) : "unknown");
        }
        return token;
    }

    /**
     * 取得或续期端锁（MCP / Web AI 会话锁）。
     * 无锁则写入 endpointId；已是本端则续期；他端持有则立即冲突。
     *
     * @param testFlowId 测试流 id
     * @param endpointId 稳定端标识（如 mcp:sessionId）
     * @param ttlSeconds 存活秒数
     * @return 本端锁句柄（heldFromClient=true，写库后不要自动释放）
     */
    public LeaseHandle acquireOrRenewEndpoint(Long testFlowId, String endpointId, int ttlSeconds) {
        if (testFlowId == null) {
            throw new ServiceException("缺少 testFlowId，无法抢写锁");
        }
        if (endpointId == null || endpointId.isBlank()) {
            throw new ServiceException("缺少端标识，无法抢写锁");
        }
        String endpoint = endpointId.trim();
        int ttl = ttlSeconds > 0 ? ttlSeconds : MCP_TTL_SECONDS;
        String key = key(testFlowId);
        Object held = redisCache.getCacheObject(key);
        if (held == null) {
            if (!redisCache.setIfAbsent(key, endpoint, ttl, TimeUnit.SECONDS)) {
                Object raced = redisCache.getCacheObject(key);
                if (raced != null && endpoint.equals(String.valueOf(raced))) {
                    redisCache.expire(key, ttl, TimeUnit.SECONDS);
                    return new LeaseHandle(endpoint, true, ttl);
                }
                throw new FlowEditLeaseConflictException(raced != null ? String.valueOf(raced) : "unknown");
            }
            return new LeaseHandle(endpoint, true, ttl);
        }
        if (endpoint.equals(String.valueOf(held))) {
            redisCache.expire(key, ttl, TimeUnit.SECONDS);
            return new LeaseHandle(endpoint, true, ttl);
        }
        throw new FlowEditLeaseConflictException(String.valueOf(held));
    }

    /**
     * 若本端已持锁则续期；未持锁或他端持有则不动并返回 false。
     * 供 MCP 读工具在该流上续期会话锁。
     */
    public boolean renewIfHeldBy(Long testFlowId, String endpointId, int ttlSeconds) {
        if (testFlowId == null || endpointId == null || endpointId.isBlank()) {
            return false;
        }
        if (!holds(testFlowId, endpointId.trim())) {
            return false;
        }
        int ttl = ttlSeconds > 0 ? ttlSeconds : MCP_TTL_SECONDS;
        return redisCache.expire(key(testFlowId), ttl, TimeUnit.SECONDS);
    }

    /**
     * 心跳续期。
     * 仅本方 token 仍有效时，把 TTL 重新设为默认秒数。
     *
     * @param testFlowId 测试流 id
     * @param token      本方租约 token
     * @return true 续期成功；false token 无效或已过期
     */
    public boolean heartbeat(Long testFlowId, String token) {
        return heartbeat(testFlowId, token, DEFAULT_TTL_SECONDS);
    }

    /**
     * 心跳续期并指定 TTL。
     */
    public boolean heartbeat(Long testFlowId, String token, int ttlSeconds) {
        if (!holds(testFlowId, token)) {
            return false;
        }
        int ttl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        return redisCache.expire(key(testFlowId), ttl, TimeUnit.SECONDS);
    }

    /**
     * 释放写锁。
     * 仅本方 token 仍有效时删除键；否则不动。
     *
     * @param testFlowId 测试流 id
     * @param token      本方租约 token / 端 ID
     */
    public void release(Long testFlowId, String token) {
        if (!holds(testFlowId, token)) {
            return;
        }
        redisCache.deleteObject(key(testFlowId));
    }

    /**
     * 写库前取得写锁。
     * <ul>
     *   <li>existingToken 仍有效 → 续期，标记为客户端/会话长持锁</li>
     *   <li>holderId 为稳定会话端（mcp: / web-autopilot:）→ 取得或续期会话锁</li>
     *   <li>其它 → 短时抢一把新锁，写完后应由调用方释放</li>
     * </ul>
     *
     * @param testFlowId    测试流 id
     * @param holderId      持锁方前缀或稳定端 ID
     * @param existingToken 客户端已持有的租约，可空
     * @return 本次占用的租约句柄
     */
    public LeaseHandle beginWrite(Long testFlowId, String holderId, String existingToken) {
        if (testFlowId == null) {
            throw new ServiceException("缺少 testFlowId，无法抢写锁");
        }
        if (existingToken != null && !existingToken.isBlank()) {
            String token = existingToken.trim();
            int ttl = isSessionEndpoint(token) || isSessionEndpoint(holderId)
                    ? MCP_TTL_SECONDS
                    : DEFAULT_TTL_SECONDS;
            if (heartbeat(testFlowId, token, ttl)) {
                return new LeaseHandle(token, true, ttl);
            }
        }
        if (isSessionEndpoint(holderId)) {
            return acquireOrRenewEndpoint(testFlowId, holderId.trim(), MCP_TTL_SECONDS);
        }
        String token = tryAcquire(testFlowId, holderId, DEFAULT_TTL_SECONDS);
        return new LeaseHandle(token, false, DEFAULT_TTL_SECONDS);
    }

    /**
     * 判断本方 token 是否仍为当前写锁。
     */
    public boolean holds(Long testFlowId, String token) {
        if (testFlowId == null || token == null || token.isBlank()) {
            return false;
        }
        Object held = redisCache.getCacheObject(key(testFlowId));
        return held != null && token.trim().equals(String.valueOf(held));
    }

    /**
     * 查询当前写锁持有方（只读，不续期）。
     * 无锁返回 null；有锁返回 Redis 中的 token 或端 ID 字符串。
     * MCP release_flow_lock 用此判断锁是否在本端名下。
     *
     * @param testFlowId 测试流 id
     * @return 持锁 token，或 null
     */
    public String peekHolder(Long testFlowId) {
        if (testFlowId == null) {
            return null;
        }
        Object held = redisCache.getCacheObject(key(testFlowId));
        return held != null ? String.valueOf(held) : null;
    }

    /**
     * 剩余存活秒数；无锁或无法读取时返回 -1。
     */
    public long remainingTtlSeconds(Long testFlowId) {
        if (testFlowId == null) {
            return -1;
        }
        try {
            return redisCache.getExpire(key(testFlowId));
        } catch (Exception e) {
            return -1;
        }
    }

    /** 是否为按会话持有的稳定端标识 */
    public static boolean isSessionEndpoint(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String s = id.trim();
        return s.startsWith("mcp:") || s.startsWith("web-autopilot:");
    }

    /**
     * 组装 MCP 端 ID。
     * 有会话用 mcp:sessionId；否则用 mcp:token:项目:操作者。
     */
    public static String mcpEndpointId(String sessionId, Long projectId, Long operatorUserId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return "mcp:" + sessionId.trim();
        }
        String project = projectId != null ? String.valueOf(projectId) : "0";
        String user = operatorUserId != null ? String.valueOf(operatorUserId) : "anon";
        return "mcp:token:" + project + ":" + user;
    }

    /**
     * 组装 Web AI 助手端 ID。
     */
    public static String webAutopilotEndpointId(Long aiChatSessionId) {
        if (aiChatSessionId == null) {
            return "web-autopilot:anon";
        }
        return "web-autopilot:" + aiChatSessionId;
    }

    /** 拼出该测试流的 Redis 写锁键 */
    private static String key(Long testFlowId) {
        return KEY_PREFIX + testFlowId;
    }

    /**
     * 一次写库占用的租约句柄。
     *
     * @param token          租约 token / 端 ID
     * @param heldFromClient true 表示会话或客户端长持锁，写完后不要释放；
     *                       false 表示本次短抢，写完后应释放
     * @param ttlSeconds     本次续期使用的 TTL，供回执展示
     */
    public record LeaseHandle(String token, boolean heldFromClient, int ttlSeconds) {
        public LeaseHandle(String token, boolean heldFromClient) {
            this(token, heldFromClient, DEFAULT_TTL_SECONDS);
        }
    }
}
