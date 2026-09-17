package com.qualitest.flow.sync;

import com.qualitest.common.core.redis.RedisCache;
import com.qualitest.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 测试流画布写锁（按 testFlowId，Redis SET NX + TTL）。
 * <p>
 * 规则：只挡写图；打开浏览不占锁。Web 脏稿可长持锁并定时心跳续期；
 * 无租约的写入（如 MCP）走短抢短释。抢锁失败抛写锁冲突异常。
 */
@Service
@RequiredArgsConstructor
public class FlowEditLeaseService {

    /** 保存请求携带的租约请求头名 */
    public static final String HEADER_NAME = "X-Flow-Edit-Lease";
    private static final String KEY_PREFIX = "qualitest:flow:edit-lease:";
    /** 租约存活秒数；心跳须在过期前刷新 */
    public static final int DEFAULT_TTL_SECONDS = 45;

    private final RedisCache redisCache;

    /**
     * 抢占写锁。
     *
     * @param testFlowId 测试流 id
     * @param holderId   持锁方前缀（如 web:用户名 / mcp），拼进 token 便于辨认
     * @return 租约 token
     */
    public String tryAcquire(Long testFlowId, String holderId) {
        if (testFlowId == null) {
            throw new ServiceException("缺少 testFlowId，无法抢写锁");
        }
        String token = (holderId != null && !holderId.isBlank() ? holderId.trim() : "anon")
                + ":" + UUID.randomUUID();
        String key = key(testFlowId);
        if (!redisCache.setIfAbsent(key, token, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS)) {
            Object held = redisCache.getCacheObject(key);
            throw new FlowEditLeaseConflictException(held != null ? String.valueOf(held) : "unknown");
        }
        return token;
    }

    /**
     * 心跳续期。仅当 Redis 中值仍等于本方 token 时延长 TTL。
     *
     * @return true=续期成功；false=token 无效或已过期
     */
    public boolean heartbeat(Long testFlowId, String token) {
        if (!holds(testFlowId, token)) {
            return false;
        }
        return redisCache.expire(key(testFlowId), DEFAULT_TTL_SECONDS);
    }

    /**
     * 释放写锁。仅持有方 token 匹配时删除，避免误删他端锁。
     */
    public void release(Long testFlowId, String token) {
        if (!holds(testFlowId, token)) {
            return;
        }
        redisCache.deleteObject(key(testFlowId));
    }

    /**
     * 写库前占锁。
     * <ul>
     *   <li>请求带有仍有效的 token → 续期，返回 heldFromClient=true（写完不要 release）</li>
     *   <li>否则短抢一把 → heldFromClient=false（写完须在 finally release）</li>
     * </ul>
     *
     * @param existingToken 客户端已持有的租约，可空
     */
    public LeaseHandle beginWrite(Long testFlowId, String holderId, String existingToken) {
        if (testFlowId == null) {
            throw new ServiceException("缺少 testFlowId，无法抢写锁");
        }
        if (existingToken != null && !existingToken.isBlank()) {
            if (heartbeat(testFlowId, existingToken.trim())) {
                return new LeaseHandle(existingToken.trim(), true);
            }
        }
        String token = tryAcquire(testFlowId, holderId);
        return new LeaseHandle(token, false);
    }

    /** 判断当前 Redis 锁值是否等于给定 token */
    private boolean holds(Long testFlowId, String token) {
        if (testFlowId == null || token == null || token.isBlank()) {
            return false;
        }
        Object held = redisCache.getCacheObject(key(testFlowId));
        return held != null && token.equals(String.valueOf(held));
    }

    private static String key(Long testFlowId) {
        return KEY_PREFIX + testFlowId;
    }

    /**
     * 一次写库占用的租约句柄。
     *
     * @param token          租约 token
     * @param heldFromClient true=沿用客户端长持锁，写完勿 release；false=本次短抢，写完须 release
     */
    public record LeaseHandle(String token, boolean heldFromClient) {
    }
}
