package com.qualitest.flow.model;

import com.qualitest.flow.contract.FlowContractDefaults;

/**
 * 返回节点保存期默认值：{@code data.from} 缺省补 {@code flow.out}。
 * <p>
 * 常量与补写逻辑已收拢到 {@link FlowContractDefaults}；本类保留兼容调用点。
 */
public final class ReturnNodeDefaults {

    /** 缺省晋升来源路径 */
    public static final String DEFAULT_FROM = FlowContractDefaults.DEFAULT_FROM;

    private ReturnNodeDefaults() {
    }

    /**
     * 就地补齐返回节点缺失 / 空白的 {@code data.from}，并顺带去掉两侧空白。
     *
     * @param graph 待落库的图，可为 null
     * @return 是否发生了改动
     */
    public static boolean fillMissingFrom(GraphJson graph) {
        return FlowContractDefaults.fillMissingReturnFrom(graph);
    }
}
