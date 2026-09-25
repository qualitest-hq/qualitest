package com.qualitest.flow.sync;

import com.qualitest.common.core.redis.RedisCache;
import com.qualitest.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 测试流画布写锁。
 * 按测试流 id 在 Redis 中 SET NX，并带 TTL。
 * 只约束写 graph_json；打开浏览不占锁。
 * Web 脏稿可长持锁并靠心跳续期；无客户端租约的写入（例如 MCP）短抢短释。
 * 抢锁失败抛出写锁冲突异常。
 */
@Service
@RequiredArgsConstructor
public class FlowEditLeaseService {

    /** 保存画布请求头中携带租约 token 的字段名 */
    public static final String HEADER_NAME = "X-Flow-Edit-Lease";
    /** Redis 键前缀，后接测试流 id */
    private static final String KEY_PREFIX = "qualitest:flow:edit-lease:";
    /** 租约默认存活秒数；心跳须在过期前调用 */
    public static final int DEFAULT_TTL_SECONDS = 30;

    private final RedisCache redisCache;

    /**
     * 抢占写锁。
     * 成功则写入 Redis 并返回新 token；已被占用则抛冲突异常。
     *
     * @param testFlowId 测试流 id
     * @param holderId   持锁方前缀（如 web:用户名、mcp），会拼进 token 便于辨认
     * @return 新租约 token（持锁方前缀 + UUID）
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
     * 心跳续期。
     * 仅本方 token 仍有效时，把 TTL 重新设为默认秒数。
     *
     * @param testFlowId 测试流 id
     * @param token      本方租约 token
     * @return true 续期成功；false token 无效或已过期
     */
    public boolean heartbeat(Long testFlowId, String token) {
        if (!holds(testFlowId, token)) {
            return false;
        }
        return redisCache.expire(key(testFlowId), DEFAULT_TTL_SECONDS);
    }

    /**
     * 释放写锁。
     * 仅本方 token 仍有效时删除键；否则不动。
     *
     * @param testFlowId 测试流 id
     * @param token      本方租约 token
     */
    public void release(Long testFlowId, String token) {
        if (!holds(testFlowId, token)) {
            return;
        }
        redisCache.deleteObject(key(testFlowId));
    }

    /**
     * 写库前取得写锁。
     * 若 existingToken 仍有效则续期，并标记为客户端长持锁；
     * 否则短时抢一把新锁，写完后应由调用方释放。
     *
     * @param testFlowId    测试流 id
     * @param holderId      持锁方前缀
     * @param existingToken 客户端已持有的租约，可空
     * @return 本次占用的租约句柄
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

    /**
     * 判断本方 token 是否仍为当前写锁。
     */
    private boolean holds(Long testFlowId, String token) {
        if (testFlowId == null || token == null || token.isBlank()) {
            return false;
        }
        Object held = redisCache.getCacheObject(key(testFlowId));
        return held != null && token.equals(String.valueOf(held));
    }

    /**
     * 查询当前写锁持有方。
     * 无锁返回 null；有锁返回 Redis 中的 token 字符串。
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

    /** 拼出该测试流的 Redis 写锁键 */
    private static String key(Long testFlowId) {
        return KEY_PREFIX + testFlowId;
    }

    /**
     * 一次写库占用的租约句柄。
     *
     * @param token          租约 token
     * @param heldFromClient true 表示沿用客户端长持锁，写完后不要释放；
     *                       false 表示本次短抢，写完后应释放
     */
    public record LeaseHandle(String token, boolean heldFromClient) {
    }
}
