package com.qualitest.flow.subflow;

/**
 * 子流嵌套深度计数。
 * <p>
 * 主流程 {@code subflowDepth=0}，每进入一层子流 +1。
 * {@link #MAX_DEPTH}=2 表示最多执行到「孙图」层（主→子→孙），在 depth=2 时禁止子图内再挂 subflow 节点。
 */
public final class SubflowDepth {

    /** 允许的最大 subflowDepth（含），即主→子→孙共 3 层执行上下文、2 层嵌套 */
    public static final int MAX_DEPTH = 2;

    private SubflowDepth() {
    }

    public static boolean canInvokeSubflow(int currentDepth) {
        return currentDepth < MAX_DEPTH;
    }
}
