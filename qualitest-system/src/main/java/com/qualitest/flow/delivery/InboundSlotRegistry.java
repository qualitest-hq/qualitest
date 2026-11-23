package com.qualitest.flow.delivery;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 开放 HTTP 入站并发名额。
 * <p>
 * 与环境侧「同时跑几条测流」的槽位分开计数：
 * 每次开放请求成功受理时 {@link #tryAcquire} 占一席，
 * {@code http-return} 或收束交付成功（或异常兜底写出）后 {@link #release} 立刻腾出，
 * 不等整张图跑完。满员时调用方应直接回 503，不再开跑。
 */
@Component
public class InboundSlotRegistry {

    /** 未另行注入时的平台总上限 */
    public static final int DEFAULT_MAX_SLOTS = 64;

    private final int maxSlots;
    /** 当前占用席数（与 active 同步增减） */
    private final AtomicInteger held = new AtomicInteger(0);
    /** 已发放、尚未释放的令牌集合 */
    private final ConcurrentHashMap<String, Boolean> active = new ConcurrentHashMap<>();

    /** 使用平台默认席位上限构造 */
    public InboundSlotRegistry() {
        this(DEFAULT_MAX_SLOTS);
    }

    /**
     * @param maxSlots 同时受理上限；小于 1 时按 1 处理
     */
    public InboundSlotRegistry(int maxSlots) {
        this.maxSlots = Math.max(1, maxSlots);
    }

    /**
     * 尝试占一席。
     *
     * @return 非空令牌表示占用成功，须在交付后 release；满员返回 null
     */
    public String tryAcquire() {
        while (true) {
            int cur = held.get();
            if (cur >= maxSlots) {
                return null;
            }
            if (held.compareAndSet(cur, cur + 1)) {
                String token = UUID.randomUUID().toString();
                active.put(token, Boolean.TRUE);
                return token;
            }
        }
    }

    /**
     * 释放一席。未知或已释放过的 token 无副作用，计数不会变为负数。
     */
    public void release(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (active.remove(token) != null) {
            held.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    /** 当前已占用席数 */
    public int heldCount() {
        return held.get();
    }

    /** 配置上限 */
    public int maxSlots() {
        return maxSlots;
    }
}
