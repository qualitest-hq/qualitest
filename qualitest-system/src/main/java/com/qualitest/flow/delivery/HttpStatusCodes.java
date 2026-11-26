package com.qualitest.flow.delivery;

/**
 * HTTP 状态码约定：开放写出 / 返回节点 / 入口兜底共用的 100–599 解析。
 */
public final class HttpStatusCodes {

    /** 合法下限（含） */
    public static final int MIN = 100;
    /** 合法上限（含） */
    public static final int MAX = 599;
    /** http-return / 写出缺省状态 */
    public static final int DEFAULT = 200;

    private HttpStatusCodes() {
    }

    /**
     * 把任意值解析为 100–599 的状态码；非法或不在范围内返回 null。
     */
    public static Integer coerce(Object raw) {
        if (raw instanceof Number n) {
            int v = n.intValue();
            return inRange(v) ? v : null;
        }
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            return inRange(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 是否落在合法 HTTP 状态码区间 */
    public static boolean inRange(int status) {
        return status >= MIN && status <= MAX;
    }
}
