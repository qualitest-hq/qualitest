package com.qualitest.flow.graph;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.validate.FlowNodeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并流程补丁时，维护条件节点出边与分支下游的绑定。
 * <p>
 * 运行时下一跳只读 {@code data.branches[].target}。新增或更新出边时把边的目标写入对应分支；
 * 删除出边时清掉指向该目标的绑定。写入 target 时会去掉分支上多余的 {@code terminal} 字段。
 */
public final class ConditionBranchMergeHelper {

    private ConditionBranchMergeHelper() {
    }

    /**
     * 条件节点出边写入图后，把边的目标同步到源节点对应分支的 {@code target}。
     * <p>
     * 按边的 {@code label} 选择分支：可为 {@code out-<分支id>}、分支 id 本身，或分支种类
     * {@code if}/{@code elif}/{@code else}。没有 label 则不绑定。
     * 若该分支原先指向别的节点，会删掉同源、指向旧目标的多余出边。
     *
     * @param nodes 当前图节点列表
     * @param edges 当前图边列表（改连时可能删掉多余边）
     * @param edge  刚写入的出边
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
        String branchId = resolveConditionBranchIdForEdge(data, edge.getTarget(), edge.getLabel());
        if (branchId == null || branchId.isBlank()) {
            return;
        }
        String staleTarget = findBranchTarget(data, branchId);
        if (staleTarget != null && !staleTarget.equals(edge.getTarget())) {
            edges.removeIf(e -> e != null
                    && !edge.getId().equals(e.getId())
                    && edge.getSource().equals(e.getSource())
                    && staleTarget.equals(e.getTarget()));
        }
        bindConditionBranchTarget(data, edge.getTarget(), branchId);
        source.setData(data);
    }

    /**
     * 删除条件节点出边前，清除各分支里指向该边目标的 {@code target}。
     *
     * @param nodes 当前图节点列表
     * @param edge  即将删除的出边
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
     * 把下游节点 id 写入指定分支的 {@code target}，并去掉该分支上的 {@code terminal}。
     * {@code branchId} 必填；为空或找不到对应分支时不修改。
     *
     * @param nodeData 条件节点 data
     * @param target   下游节点 id
     * @param branchId 分支 id
     * @return 是否改写了 branches
     */
    static boolean bindConditionBranchTarget(Map<String, Object> nodeData, String target, String branchId) {
        if (target == null || target.isBlank() || nodeData == null
                || branchId == null || branchId.isBlank()) {
            return false;
        }
        List<Map<String, Object>> branches = copyBranches(nodeData);
        if (branches.isEmpty()) {
            return false;
        }
        for (Map<String, Object> branch : branches) {
            if (branchId.equals(stringValue(branch.get("id")))) {
                if (target.equals(stringValue(branch.get("target")))) {
                    return false;
                }
                branch.put("target", target);
                branch.remove("terminal");
                nodeData.put("branches", branches);
                return true;
            }
        }
        return false;
    }

    /**
     * 清除所有指向给定下游节点 id 的分支 {@code target}。
     *
     * @param nodeData 条件节点 data
     * @param target   要解除绑定的下游节点 id
     * @return 是否改写了 branches
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
     * 根据边的目标与 label，解析应写入的分支 id。
     * <p>
     * 优先用 label 匹配分支；若无 label 匹配，但某分支已绑定同一 target，则返回该分支 id（重复写入时幂等）。
     * 仍无法判定时返回 {@code null}。
     *
     * @param nodeData  条件节点 data
     * @param target    边的目标节点 id
     * @param edgeLabel 边的 label，可为空
     * @return 分支 id，无法判定时为 null
     */
    static String resolveConditionBranchIdForEdge(
            Map<String, Object> nodeData,
            String target,
            String edgeLabel
    ) {
        List<Map<String, Object>> branches = readBranches(nodeData);
        String byLabel = matchBranchIdByEdgeLabel(branches, edgeLabel);
        if (byLabel != null) {
            return byLabel;
        }
        if (target != null && !target.isBlank()) {
            for (Map<String, Object> branch : branches) {
                if (target.equals(stringValue(branch.get("target")))) {
                    return stringValue(branch.get("id"));
                }
            }
        }
        return null;
    }

    /**
     * 用边 label 匹配分支 id。
     * 匹配顺序：{@code out-<分支id>} → 分支 id 原文 → 分支种类 {@code if}/{@code elif}/{@code else}。
     * label 为空时返回 {@code null}。
     *
     * @param branches  分支列表
     * @param edgeLabel 边的 label
     * @return 匹配到的分支 id，未匹配为 null
     */
    static String matchBranchIdByEdgeLabel(List<Map<String, Object>> branches, String edgeLabel) {
        if (edgeLabel == null || edgeLabel.isBlank() || branches.isEmpty()) {
            return null;
        }
        String label = edgeLabel.trim();
        if (label.length() > 4 && label.regionMatches(true, 0, "out-", 0, 4)) {
            String handleId = label.substring(4).trim();
            String byHandle = findBranchIdByField(branches, "id", handleId);
            if (byHandle != null) {
                return byHandle;
            }
        }
        String byId = findBranchIdByField(branches, "id", label);
        if (byId != null) {
            return byId;
        }
        return findBranchIdByField(branches, "kind", label);
    }

    /**
     * 在分支列表中按指定字段做忽略大小写相等匹配，返回该分支的 id。
     *
     * @param branches 分支列表
     * @param field    字段名，如 id、kind
     * @param expected 期望值
     * @return 分支 id，未匹配为 null
     */
    private static String findBranchIdByField(
            List<Map<String, Object>> branches,
            String field,
            String expected
    ) {
        for (Map<String, Object> branch : branches) {
            String value = stringValue(branch.get(field));
            if (expected.equalsIgnoreCase(value)) {
                return stringValue(branch.get("id"));
            }
        }
        return null;
    }

    /** 读取指定分支当前的 target，没有则返回 null。 */
    private static String findBranchTarget(Map<String, Object> nodeData, String branchId) {
        for (Map<String, Object> branch : readBranches(nodeData)) {
            if (branchId.equals(stringValue(branch.get("id")))) {
                return stringValue(branch.get("target"));
            }
        }
        return null;
    }

    /** 复制节点 data 为可写 Map，避免直接改只读结构。 */
    private static Map<String, Object> ensureMutableData(GraphNode node) {
        Map<String, Object> data = node.getData();
        if (data == null) {
            data = new HashMap<>();
        } else {
            data = new HashMap<>(data);
        }
        return data;
    }

    /** 深拷贝读出 branches 列表；无有效列表时返回空。 */
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

    /** 在可读拷贝基础上再包一层可增删的 ArrayList。 */
    private static List<Map<String, Object>> copyBranches(Map<String, Object> nodeData) {
        return new ArrayList<>(readBranches(nodeData));
    }

    /** 对象转字符串；null 仍为 null。 */
    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
