package com.qualitest.flow.graph;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;

import java.util.List;

/**
 * 测试流图节点与连线的按 id 查找工具。
 * <p>
 * 用于 patch 合并、condition 分支同步等需要在节点/边列表中定位元素的场景。
 * 未找到时返回 {@code null}，不抛异常。
 */
public final class GraphLookupUtils {

    private GraphLookupUtils() {
    }

    /**
     * 在节点列表中按 id 查找。
     *
     * @param nodes 待搜索的节点列表，可为 null
     * @param id    目标节点 id，可为 null
     * @return 匹配的节点；列表为空、id 为空或未找到时返回 null
     */
    public static GraphNode findNode(List<GraphNode> nodes, String id) {
        if (nodes == null || id == null) {
            return null;
        }
        for (GraphNode node : nodes) {
            if (node != null && id.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }

    /**
     * 在边列表中按 id 查找。
     *
     * @param edges 待搜索的边列表，可为 null
     * @param id    目标边 id，可为 null
     * @return 匹配的边；列表为空、id 为空或未找到时返回 null
     */
    public static GraphEdge findEdge(List<GraphEdge> edges, String id) {
        if (edges == null || id == null) {
            return null;
        }
        for (GraphEdge edge : edges) {
            if (edge != null && id.equals(edge.getId())) {
                return edge;
            }
        }
        return null;
    }
}
