package com.qualitest.flow.run;

import com.qualitest.flow.model.GraphNode;

/**
 * 读取节点 data.alwaysRun。
 * <p>
 * 为 true 时：业务步骤失败后进入失败后继续阶段，本节点仍会执行；
 * 执行成功记 continued，失败记 continued_failed。
 * 未标记的节点在该阶段记 bypassed，不调用 Handler。
 */
public final class GraphNodeAlwaysRunSupport {

    /** 节点 data 键名：失败后是否仍执行本节点 */
    public static final String KEY_ALWAYS_RUN = "alwaysRun";

    private GraphNodeAlwaysRunSupport() {
    }

    /**
     * 判断节点是否开启始终执行。
     * 仅认布尔 true；缺省或其它类型为 false。
     *
     * @param node 图节点，可为 null
     * @return 是否始终执行
     */
    public static boolean isAlwaysRun(GraphNode node) {
        if (node == null || node.getData() == null) {
            return false;
        }
        return Boolean.TRUE.equals(node.getData().get(KEY_ALWAYS_RUN));
    }
}
