package com.qualitest.flow.sync;

/**
 * 测试流写锁已被其它端占用时抛出。
 * 携带当前持锁方摘要，便于前端 Toast 与 MCP 回执提示「换一流或稍后重试」。
 */
public final class FlowEditLeaseConflictException extends RuntimeException {

    /** 当前持锁方标识，如 web:admin:uuid 或 mcp:... */
    private final String lockHeldBy;

    public FlowEditLeaseConflictException(String lockHeldBy) {
        super("测试流正在被其它端写入（lockHeldBy="
                + (lockHeldBy != null ? lockHeldBy : "unknown")
                + "），请稍后重试或换一流");
        this.lockHeldBy = lockHeldBy != null ? lockHeldBy : "unknown";
    }

    /** 当前持锁方摘要 */
    public String getLockHeldBy() {
        return lockHeldBy;
    }
}
