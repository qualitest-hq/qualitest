package com.qualitest.flow.graph;

import com.qualitest.flow.graph.GraphLookupUtils;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.validate.FlowNodeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并 patch 时维护 condition 出边与 data.branches[].target 的绑定。
 * <p>
 * 遍历下一跳读的是 branches[].target；新增/更新出边时回写 target，删除出边时清掉对应绑定。
 * 绑定成功时顺带删掉分支上的遗留 terminal 字段。
 */
public final class ConditionBranchMergeHelper {

    private ConditionBranchMergeHelper() {
    }

    /**
     * condition 出边写入图后，同步更新源节点 branches[].target。
     * 若分支此前指向其他节点，会移除同源到旧 target 的冗余出边（分支改连场景）。
     */
    public static void syncConditionEdgeToNodes(List<GraphNode> nodes, List<GraphEdge> edges, GraphEdge edge) {
        if (edge == null || edge.getSource() == null || edge.getTarget() == null) {
            return;
        }
        GraphNode source = GraphLookupUtils.findNode(nodes, edge.getSource());
        if (source == null || !FlowNodeType.CONDITION.matches(source.getType())) {
            return;
        }
        Map<String, Object> data = ensureMutableData(source);
        String branchId = resolveConditionBranchIdForEdge(data, edge.getTarget(), edge.getSource(), edges);
        if (branchId != null) {
            String staleTarget = findBranchTarget(data, branchId);
            if (staleTarget != null && !staleTarget.equals(edge.getTarget())) {
                edges.removeIf(e -> e != null
                        && !edge.getId().equals(e.getId())
                        && edge.getSource().equals(e.getSource())
                        && staleTarget.equals(e.getTarget()));
            }
        }
        bindConditionBranchTarget(data, edge.getTarget(), branchId);
        source.setData(data);
    }

    /**
     * 删除 condition 出边前，清除 branches 中指向该 target 的绑定。
     */
    public static void clearConditionTargetForRemovedEdge(List<GraphNode> nodes, GraphEdge edge) {
        if (edge == null || edge.getSource() == null || edge.getTarget() == null) {
            return;
        }
        GraphNode source = GraphLookupUtils.findNode(nodes, edge.getSource());
        if (source == null || !FlowNodeType.CONDITION.matches(source.getType())) {
            return;
        }
        Map<String, Object> data = ensureMutableData(source);
        if (clearConditionBranchTarget(data, edge.getTarget())) {
            source.setData(data);
        }
    }

    /**
     * 将出边 target 写入 branches[].target。
     * <p>
     * branchId 非空：写入对应分支。<br>
     * branchId 为空：若已有分支指向同一 target 则跳过；否则写入首条尚无有效 target 的分支。<br>
     * 写入后删除该分支上的遗留 terminal 字段。
     *
     * @return 是否修改了 branches
     */
    static boolean bindConditionBranchTarget(Map<String, Object> nodeData, String target, String branchId) {
        if (target == null || target.isBlank() || nodeData == null) {
            return false;
        }
        List<Map<String, Object>> branches = copyBranches(nodeData);
        if (branches.isEmpty()) {
            return false;
        }
        if (branchId == null || branchId.isBlank()) {
            if (branches.stream().anyMatch(b -> target.equals(stringValue(b.get("target"))))) {
                return false;
            }
            Map<String, Object> unbound = branches.stream()
                    .filter(b -> !ConditionBranchTerminalSupport.hasBranchTarget(b))
                    .findFirst()
                    .orElse(null);
            if (unbound == null) {
                return false;
            }
            branchId = stringValue(unbound.get("id"));
        }
        for (Map<String, Object> branch : branches) {
            if (branchId.equals(stringValue(branch.get("id")))) {
                if (target.equals(stringValue(branch.get("target")))) {
                    return false;
                }
                branch.put("target", target);
                // 有出口后不再保留旧 terminal 字段
                ConditionBranchTerminalSupport.stripTerminalFlag(branch);
                nodeData.put("branches", branches);
                return true;
            }
        }
        return false;
    }

    /**
     * 清除 branches 中所有指向给定 target 的绑定。
     */
    static boolean clearConditionBranchTarget(Map<String, Object> nodeData, String target) {
        if (target == null || target.isBlank() || nodeData == null) {
            return false;
        }
        List<Map<String, Object>> branches = copyBranches(nodeData);
        if (branches.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Map<String, Object> branch : branches) {
            if (target.equals(stringValue(branch.get("target")))) {
                branch.remove("target");
                changed = true;
            }
        }
        if (changed) {
            nodeData.put("branches", branches);
        }
        return changed;
    }

    /**
     * 解析出边应关联的分支 id。
     * 顺序：已绑定相同 target 的分支 → 画布上存在旧出边的已绑定分支（改连）→ 首条未绑定分支。
     */
    static String resolveConditionBranchIdForEdge(
            Map<String, Object> nodeData,
            String target,
            String source,
            List<GraphEdge> edges
    ) {
        List<Map<String, Object>> branches = readBranches(nodeData);
        if (target != null && !target.isBlank()) {
            for (Map<String, Object> branch : branches) {
                if (target.equals(stringValue(branch.get("target")))) {
                    return stringValue(branch.get("id"));
                }
            }
        }
        if (source != null && edges != null) {
            String rebindingId = null;
            for (Map<String, Object> branch : branches) {
                String branchTarget = stringValue(branch.get("target"));
                if (isBlank(branchTarget) || branchTarget.equals(target)) {
                    continue;
                }
                boolean hasOutgoing = false;
                for (GraphEdge e : edges) {
                    if (e != null && source.equals(e.getSource()) && branchTarget.equals(e.getTarget())) {
                        hasOutgoing = true;
                        break;
                    }
                }
                if (!hasOutgoing) {
                    continue;
                }
                if (rebindingId != null) {
                    return null;
                }
                rebindingId = stringValue(branch.get("id"));
            }
            if (rebindingId != null) {
                return rebindingId;
            }
        }
        for (Map<String, Object> branch : branches) {
            if (isBlank(branch.get("target"))
                    && !ConditionBranchTerminalSupport.isTerminalBranch(branch)) {
                return stringValue(branch.get("id"));
            }
        }
        return null;
    }

    private static String findBranchTarget(Map<String, Object> nodeData, String branchId) {
        for (Map<String, Object> branch : readBranches(nodeData)) {
            if (branchId.equals(stringValue(branch.get("id")))) {
                return stringValue(branch.get("target"));
            }
        }
        return null;
    }

    private static Map<String, Object> ensureMutableData(GraphNode node) {
        Map<String, Object> data = node.getData();
        if (data == null) {
            data = new HashMap<>();
        } else {
            data = new HashMap<>(data);
        }
        return data;
    }

    private static List<Map<String, Object>> readBranches(Map<String, Object> nodeData) {
        if (nodeData == null) {
            return List.of();
        }
        Object branchesObj = nodeData.get("branches");
        if (!(branchesObj instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new HashMap<>();
                map.forEach((k, v) -> {
                    if (k != null) {
                        copy.put(String.valueOf(k), v);
                    }
                });
                out.add(copy);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> copyBranches(Map<String, Object> nodeData) {
        return new ArrayList<>(readBranches(nodeData));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
