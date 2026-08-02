package com.qualitest.flow.delay;

/**
 * Delay 节点毫秒上下限与默认值（设计态规范化与运行态共用）。
 */
public final class DelayConstants {

    /** 缺省等待毫秒 */
    public static final long DEFAULT_DELAY_MS = 1_000L;

    /** 单步最大等待毫秒，防止配置过大拖垮执行线程 */
    public static final long MAX_DELAY_MS = 60_000L;

    private DelayConstants() {
    }

    /**
     * 规范化 delay ms：空/非法 → 默认；负 → 0；超出上限则截断到上限。
     * 设计态 Normalizer 用；运行态 Handler 对超限抛错（不截断）。
     */
    public static long normalizeMsForDesign(Object raw) {
        long ms = DEFAULT_DELAY_MS;
        if (raw instanceof Number n) {
            ms = n.longValue();
        } else if (raw != null) {
            String s = String.valueOf(raw).trim();
            if (!s.isEmpty()) {
                try {
                    ms = Long.parseLong(s);
                } catch (NumberFormatException ignored) {
                    ms = DEFAULT_DELAY_MS;
                }
            }
        }
        if (ms < 0) {
            ms = 0;
        }
        if (ms > MAX_DELAY_MS) {
            ms = MAX_DELAY_MS;
        }
        return ms;
    }

    /** 解析 ms；空/非法返回 null（供校验区分「缺省可补」与「不可解析」）。 */
    public static Long tryParseMs(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
