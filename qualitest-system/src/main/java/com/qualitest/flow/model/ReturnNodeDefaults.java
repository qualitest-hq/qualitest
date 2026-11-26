package com.qualitest.flow.model;

import com.qualitest.flow.validate.FlowNodeType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 返回节点保存期默认值：{@code data.from} 缺省补 {@code flow.out}（契约 §4.1）。
 * <p>
 * 只在落库前调用；运行期不替用户补，缺 from 仍按非法图拦。
 */
public final class ReturnNodeDefaults {

    /** 缺省晋升来源路径 */
    public static final String DEFAULT_FROM = "flow.out";

    private ReturnNodeDefaults() {
    }

    /**
     * 就地补齐返回节点缺失 / 空白的 {@code data.from}，并顺带去掉两侧空白。
     *
     * @param graph 待落库的图，可为 null
     * @return 是否发生了改动
     */
    public static boolean fillMissingFrom(GraphJson graph) {
        if (graph == null || graph.getNodes() == null) {
            return false;
        }
        boolean changed = false;
        for (GraphNode node : graph.getNodes()) {
            if (node == null || !FlowNodeType.isReturn(node.getType())) {
                continue;
            }
            Map<String, Object> data = node.getData();
            if (data == null) {
                data = new LinkedHashMap<>();
                node.setData(data);
            }
            Object raw = data.get("from");
            String from = raw == null ? "" : String.valueOf(raw).trim();
            if (from.isEmpty()) {
                data.put("from", DEFAULT_FROM);
                changed = true;
                continue;
            }
            if (!from.equals(String.valueOf(raw))) {
                data.put("from", from);
                changed = true;
            }
        }
        return changed;
    }
}
