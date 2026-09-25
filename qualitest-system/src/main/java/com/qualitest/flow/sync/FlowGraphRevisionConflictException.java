package com.qualitest.flow.sync;

/**
 * 测试流图版本冲突。
 * 写 graph_json 时客户端基准版本对不上库中当前值。
 */
public final class FlowGraphRevisionConflictException extends RuntimeException {

    /** 库中当前图版本号 */
    private final long currentGraphRevision;

    /**
     * @param currentGraphRevision 库中当前图版本号
     */
    public FlowGraphRevisionConflictException(long currentGraphRevision) {
        super("测试流图版本冲突（graphRevision=" + currentGraphRevision + "）");
        this.currentGraphRevision = currentGraphRevision;
    }

    /** 库中当前图版本号 */
    public long getCurrentGraphRevision() {
        return currentGraphRevision;
    }
}
