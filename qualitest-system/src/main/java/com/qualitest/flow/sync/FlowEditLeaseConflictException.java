package com.qualitest.flow.sync;

/**
 * 测试流写锁冲突异常。
 * 他端已持锁时抛出；消息只陈述占用事实与 lockHeldBy，操作指引由调用方自行组装。
 */
public final class FlowEditLeaseConflictException extends RuntimeException {

    /**
     * 当前持锁方标识。
     * 形如 web:用户名:uuid 或 mcp:…；未知时为 unknown。
     */
    private final String lockHeldBy;

    /**
     * @param lockHeldBy 当前持锁方标识，可空（空则记为 unknown）
     */
    public FlowEditLeaseConflictException(String lockHeldBy) {
        super("测试流写锁仍被占用（lockHeldBy="
                + (lockHeldBy != null && !lockHeldBy.isBlank() ? lockHeldBy : "unknown")
                + "）");
        this.lockHeldBy = lockHeldBy != null && !lockHeldBy.isBlank() ? lockHeldBy : "unknown";
    }

    /**
     * @return 当前持锁方标识
     */
    public String getLockHeldBy() {
        return lockHeldBy;
    }
}
