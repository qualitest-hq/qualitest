package com.qualitest.flow.run;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.FlowNodeType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 隐式扇出（非 condition 多出边）的静态分析辅助。
 * <p>
 * 用于设计期判断各扇出臂是否可达返回节点，避免多臂二次交付。
 */
public final class FanoutSupport {

    private FanoutSupport() {
    }

    /**
     * 从臂起点沿出边（含 condition 全部分支）是否能碰到返回节点。
     * 遇环则停止该路径；嵌套扇出继续向下探。
     */
    public static boolean armCanReachReturn(GraphWalker walker, String armStart) {
        if (walker == null || armStart == null || armStart.isBlank()) {
            return false;
        }
        return dfsReturnReachable(walker, armStart, new HashSet<>());
    }

    private static boolean dfsReturnReachable(GraphWalker walker, String nodeId, Set<String> path) {
        if (nodeId == null || path.contains(nodeId)) {
            return false;
        }
        GraphNode node = walker.getNode(nodeId);
        if (node == null) {
            return false;
        }
        if (FlowNodeType.isReturn(node.getType())) {
            return true;
        }
        Set<String> nextPath = new HashSet<>(path);
        nextPath.add(nodeId);
        if (FlowNodeType.CONDITION.matches(node.getType())) {
            boolean any = false;
            for (String target : walker.listConditionBranchTargets(node)) {
                any |= dfsReturnReachable(walker, target, nextPath);
            }
            return any;
        }
        List<GraphEdge> outs = walker.listOutgoingEdges(nodeId);
        boolean any = false;
        for (GraphEdge edge : outs) {
            if (edge != null && edge.getTarget() != null) {
                any |= dfsReturnReachable(walker, edge.getTarget(), nextPath);
            }
        }
        return any;
    }
}
