package com.qualitest.flow.sync;

import com.qualitest.common.core.redis.RedisCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 FlowEditLeaseService：同端重入续期、他端抢锁冲突、释放与续期权限。
 * 边界：内存假 Redis，无真实 Redis。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowEditLeaseServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowEditLeaseServiceTest {

    private FlowEditLeaseService service;
    private FakeRedis redis;

    @BeforeEach
    void setUp() {
        redis = new FakeRedis();
        service = new FlowEditLeaseService(redis);
    }

    /**
     * 前提：端 A 取得锁后再调用 acquireOrRenew。
     * 期望：不抛冲突，仍持有同一端标识。
     */
    @Test
    @Order(1)
    @DisplayName("同端可重入并续期")
    void acquireOrRenew_sameEndpoint_renews() {
        FlowEditLeaseService.LeaseHandle first =
                service.acquireOrRenewEndpoint(1L, "mcp:s1", 60);
        FlowEditLeaseService.LeaseHandle second =
                service.acquireOrRenewEndpoint(1L, "mcp:s1", 60);
        assertEquals("mcp:s1", first.token());
        assertEquals("mcp:s1", second.token());
        assertTrue(second.heldFromClient());
        assertTrue(service.holds(1L, "mcp:s1"));
    }

    /**
     * 前提：端 A 持锁，端 B 抢锁。
     * 期望：立即抛冲突，lockHeldBy=端 A。
     */
    @Test
    @Order(2)
    @DisplayName("他端持锁时立即冲突")
    void acquireOrRenew_otherEndpoint_conflicts() {
        service.acquireOrRenewEndpoint(2L, "mcp:a", 60);
        FlowEditLeaseConflictException ex = assertThrows(
                FlowEditLeaseConflictException.class,
                () -> service.acquireOrRenewEndpoint(2L, "mcp:b", 60));
        assertEquals("mcp:a", ex.getLockHeldBy());
    }

    /**
     * 前提：端 A 释放后，端 B 抢锁。
     * 期望：端 B 成功取得。
     */
    @Test
    @Order(3)
    @DisplayName("释放后其它端可取得锁")
    void release_allowsOtherEndpoint() {
        service.acquireOrRenewEndpoint(3L, "mcp:a", 60);
        service.release(3L, "mcp:a");
        FlowEditLeaseService.LeaseHandle next =
                service.acquireOrRenewEndpoint(3L, "mcp:b", 60);
        assertEquals("mcp:b", next.token());
    }

    /**
     * 前提：本端已持锁，调用 renewIfHeldBy。
     * 期望：返回 true；他端调用返回 false。
     */
    @Test
    @Order(4)
    @DisplayName("读工具续期仅本端生效")
    void renewIfHeldBy_onlyOwnEndpoint() {
        service.acquireOrRenewEndpoint(4L, "mcp:a", 60);
        assertTrue(service.renewIfHeldBy(4L, "mcp:a", 60));
        assertFalse(service.renewIfHeldBy(4L, "mcp:b", 60));
    }

    /**
     * 前提：端 A 持锁，端 B 调用 release。
     * 期望：锁仍在端 A（无副作用）。
     */
    @Test
    @Order(5)
    @DisplayName("释放非本端锁无副作用")
    void release_otherEndpoint_noop() {
        service.acquireOrRenewEndpoint(5L, "mcp:a", 60);
        service.release(5L, "mcp:b");
        assertTrue(service.holds(5L, "mcp:a"));
    }

    /** 内存假 Redis：仅支持本测所需 setIfAbsent / get / expire / delete */
    private static final class FakeRedis extends RedisCache {
        private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicLong> ttl = new ConcurrentHashMap<>();

        @Override
        public <T> boolean setIfAbsent(final String key, final T value, final long timeout, final TimeUnit timeUnit) {
            Object prev = store.putIfAbsent(key, value);
            if (prev == null) {
                ttl.put(key, new AtomicLong(timeout));
                return true;
            }
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCacheObject(final String key) {
            return (T) store.get(key);
        }

        @Override
        public boolean expire(final String key, final long timeout, final TimeUnit unit) {
            if (!store.containsKey(key)) {
                return false;
            }
            ttl.put(key, new AtomicLong(timeout));
            return true;
        }

        @Override
        public boolean expire(final String key, final long timeout) {
            return expire(key, timeout, TimeUnit.SECONDS);
        }

        @Override
        public boolean deleteObject(final String key) {
            store.remove(key);
            ttl.remove(key);
            return true;
        }

        @Override
        public long getExpire(final String key) {
            AtomicLong t = ttl.get(key);
            return t != null ? t.get() : -1;
        }
    }
}
