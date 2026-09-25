package com.qualitest.flow.sync;

/**
 * 测试流写锁冲突异常。
 * 抢锁失败时抛出，消息中带上当前持锁方标识，供接口错误返回与调用方提示。
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
                + "）。可能是本账号其它标签页或 MCP/其它端正在写入；"
                + "请关闭其它画布标签或约 30 秒后重试");
        this.lockHeldBy = lockHeldBy != null && !lockHeldBy.isBlank() ? lockHeldBy : "unknown";
    }

    /**
     * @return 当前持锁方标识
     */
    public String getLockHeldBy() {
        return lockHeldBy;
    }
}
