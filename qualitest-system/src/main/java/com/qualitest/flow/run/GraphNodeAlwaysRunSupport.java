package com.qualitest.flow.run;

import com.qualitest.flow.model.GraphNode;

/**
 * 读取节点 data.alwaysRun（失败后是否仍执行本节点）。
 * <p>
 * 布尔 true：业务步骤失败后进入失败后继续阶段，本节点仍会执行；
 * 执行成功记 continued，失败记 continued_failed。
 * 未标记或非布尔 true：该阶段记 bypassed，不调用 Handler。
 * 保存时若写出该字段，值须为 JSON 布尔，且仅允许挂在 http / script / assign / subflow。
 */
public final class GraphNodeAlwaysRunSupport {

    /** 节点 data 键名：业务失败后是否仍执行本节点 */
    public static final String KEY_ALWAYS_RUN = "alwaysRun";

    private GraphNodeAlwaysRunSupport() {
    }

    /**
     * 判断节点是否开启始终执行。
     * 只认 data.alwaysRun 为布尔 true；缺省、false、字符串、数字一律视为未开启。
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
