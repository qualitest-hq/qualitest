package com.qualitest.flow.run;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.graph.ConditionBranchTargetSupport;
import com.qualitest.flow.validate.FlowNodeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程图运行时遍历器。
 * <p>
 * 供 {@link TestFlowExecutor} 在单步执行后解析下一节点 id；
 * 不预展开全路径，支持回边构成的轮询子图。
 * <ul>
 *   <li>condition：按本步 branchTaken 只走一臂</li>
 *   <li>其它节点仅一条出边：沿该边前进</li>
 *   <li>其它节点多条出边：由运行器对各下游依次扇出；本类 {@link #resolveNextNodeId} 不解析单一下一跳</li>
 * </ul>
 */
public class GraphWalker {

    /** 场景未配置步数上限时，单次 Run 采用的默认最大步数 */
    public static final int DEFAULT_MAX_STEPS = 200;

    /** 场景配置的步数上限可被裁剪到的最大值 */
    public static final int MAX_STEPS_CEILING = 1000;

    /** 节点 id → 节点定义 */
    private final Map<String, GraphNode> nodeById;
    /** 源节点 id → 出边列表 */
    private final Map<String, List<GraphEdge>> outEdgesBySource;
    /** 目标节点 id → 入边列表（用于统计开始节点） */
    private final Map<String, List<GraphEdge>> inEdgesByTarget;

    /**
     * 根据固化图快照构建邻接索引。
     *
     * @param graph 触发 Run 时深拷贝的 graph_json
     */
    public GraphWalker(GraphJson graph) {
        this.nodeById = new HashMap<>();
        this.outEdgesBySource = new HashMap<>();
        this.inEdgesByTarget = new HashMap<>();
        if (graph != null && graph.getNodes() != null) {
            for (GraphNode node : graph.getNodes()) {
                if (node != null && node.getId() != null) {
                    nodeById.put(node.getId(), node);
                }
            }
        }
        if (graph != null && graph.getEdges() != null) {
            for (GraphEdge edge : graph.getEdges()) {
                if (edge == null || edge.getSource() == null) {
                    continue;
                }
                outEdgesBySource.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge);
                if (edge.getTarget() != null) {
                    inEdgesByTarget.computeIfAbsent(edge.getTarget(), k -> new ArrayList<>()).add(edge);
                }
            }
        }
    }

    /**
     * 按 id 取节点定义；不存在时返回 null。
     */
    public GraphNode getNode(String nodeId) {
        return nodeById.get(nodeId);
    }

    /**
     * 返回唯一无入边的开始节点 id。
     */
    public String findUniqueStartNodeId() {
        List<String> startIds = nodeById.keySet().stream()
                .filter(id -> inEdgesByTarget.getOrDefault(id, List.of()).isEmpty())
                .sorted()
                .collect(Collectors.toList());
        if (startIds.isEmpty()) {
            throw new FlowExecutionException(FlowErrorCode.TF_START_NODE, "未找到开始节点");
        }
        if (startIds.size() > 1) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_START_NODE,
                    "开始节点不唯一: " + String.join(", ", startIds)
            );
        }
        return startIds.get(0);
    }

    /**
     * 解析开跑起点。
     * <ol>
     *   <li>ctx/调用方传入的 entryNodeId 非空：必须在图中存在，否则报开始节点错误</li>
     *   <li>否则若图上恰好一个入口类型节点，用该节点</li>
     *   <li>多个入口且未指定 id：报错，要求显式指定</li>
     *   <li>无入口：无法解析起点（图须配置入口节点）</li>
     * </ol>
     */
    public String resolveStartNodeId(String entryNodeId) {
        if (entryNodeId != null && !entryNodeId.isBlank()) {
            String id = entryNodeId.trim();
            if (!nodeById.containsKey(id)) {
                throw new FlowExecutionException(FlowErrorCode.TF_START_NODE, "入口节点不存在: " + id);
            }
            return id;
        }
        List<String> entryIds = nodeById.values().stream()
                .filter(n -> n != null && FlowNodeType.isEntry(n.getType()))
                .map(GraphNode::getId)
                .filter(id -> id != null && !id.isBlank())
                .sorted()
                .collect(Collectors.toList());
        if (entryIds.size() == 1) {
            return entryIds.get(0);
        }
        if (entryIds.size() > 1) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_START_NODE,
                    "存在多个入口，须指定 entryNodeId: " + String.join(", ", entryIds));
        }
        throw new FlowExecutionException(
                FlowErrorCode.TF_START_NODE,
                "须至少配置一个入口节点（场景 / HTTP / 子流入口）才能开跑");
    }

    /**
     * 判断节点是否为隐式扇出点：非 condition 且出边不少于 2 条。
     */
    public boolean isFanoutNode(GraphNode node) {
        if (node == null || node.getId() == null) {
            return false;
        }
        if (FlowNodeType.CONDITION.matches(node.getType())) {
            return false;
        }
        return outEdgesBySource.getOrDefault(node.getId(), List.of()).size() > 1;
    }

    /**
     * 根据当前节点与步骤结果解析下一节点 id；无后继或命中结束分支时返回 null。
     * 非 condition 多出边由运行器扇出，调用本方法会抛图非法异常。
     *
     * @param node       刚执行完的节点
     * @param stepResult 该步执行结果（condition 需含 branchTaken）
     */
    public String resolveNextNodeId(GraphNode node, StepResult stepResult) {
        if (node == null) {
            return null;
        }
        String type = node.getType();
        if (FlowNodeType.CONDITION.getCode().equals(type)) {
            return resolveConditionTarget(node, stepResult);
        }
        List<GraphEdge> outEdges = outEdgesBySource.getOrDefault(node.getId(), List.of());
        if (outEdges.isEmpty()) {
            return null;
        }
        if (outEdges.size() > 1) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_GRAPH_INVALID,
                    "当前节点存在多条出边，须由运行器扇出各下游: " + node.getId()
            );
        }
        return outEdges.get(0).getTarget();
    }

    /**
     * 按图内登记顺序列出节点全部出边（返回副本）。
     *
     * @param nodeId 源节点 id
     * @return 出边列表；节点 id 为空或无出边时返回空列表
     */
    public List<GraphEdge> listOutgoingEdges(String nodeId) {
        if (nodeId == null) {
            return List.of();
        }
        List<GraphEdge> edges = outEdgesBySource.getOrDefault(nodeId, List.of());
        return edges.isEmpty() ? List.of() : List.copyOf(edges);
    }

    /**
     * 列出 condition 节点各分支的有效下游节点 id。
     * <p>
     * 用于失败后继续：condition 尚未求值时无法得到 branchTaken，
     * 按各分支 target 扇出，只执行 alwaysRun，汇合节点靠已访问集合去重。
     * 无有效 target 的分支（表示流结束）不计入；同一 target 只出现一次，顺序按 branches 数组排列。
     *
     * @param node condition 节点
     * @return 下游节点 id 列表，可能为空
     */
    public List<String> listConditionBranchTargets(GraphNode node) {
        if (node == null || node.getData() == null) {
            return List.of();
        }
        Object branchesObj = node.getData().get("branches");
        if (!(branchesObj instanceof JSONArray branches)) {
            return List.of();
        }
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < branches.size(); i++) {
            JSONObject branch = branches.getJSONObject(i);
            if (branch == null || !ConditionBranchTargetSupport.hasBranchTarget(branch)) {
                continue;
            }
            String target = branch.getString("target");
            if (target == null || target.isBlank() || !nodeById.containsKey(target)) {
                continue;
            }
            if (!targets.contains(target)) {
                targets.add(target);
            }
        }
        return targets;
    }

    /**
     * 查找从 fromId 到 toId 的边 id，用于下一步的入边记录。
     */
    public String findEdgeId(String fromId, String toId) {
        if (fromId == null || toId == null) {
            return null;
        }
        for (GraphEdge edge : outEdgesBySource.getOrDefault(fromId, List.of())) {
            if (toId.equals(edge.getTarget())) {
                return edge.getId();
            }
        }
        return null;
    }

    /**
     * 查找指向目标节点的任意入边 id（续跑时记录 incomingEdgeId）。
     */
    public String findIncomingEdgeId(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        for (List<GraphEdge> inEdges : inEdgesByTarget.values()) {
            for (GraphEdge edge : inEdges) {
                if (edge != null && nodeId.equals(edge.getTarget())) {
                    return edge.getId();
                }
            }
        }
        return null;
    }

    /**
     * 根据 condition 步骤的 branchTaken.branchId，解析下一节点 id。
     * <ul>
     *   <li>命中结束分支（无有效 target）→ 返回 null，本流结束</li>
     *   <li>命中有 target 的分支 → 返回该 target（节点须存在）</li>
     *   <li>缺少 branchTaken / 找不到分支配置 → 抛执行异常</li>
     * </ul>
     */
    private String resolveConditionTarget(GraphNode node, StepResult stepResult) {
        if (stepResult == null || stepResult.getBranchTaken() == null) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_BRANCH_UNWIRED,
                    "condition 步骤缺少 branchTaken: " + node.getId()
            );
        }
        Object branchIdObj = stepResult.getBranchTaken().get("branchId");
        String branchId = branchIdObj != null ? String.valueOf(branchIdObj) : null;
        if (branchId == null || branchId.isBlank()) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_BRANCH_UNWIRED,
                    "condition 分支 id 为空: " + node.getId()
            );
        }

        Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
        Object branchesObj = data.get("branches");
        if (!(branchesObj instanceof JSONArray branches)) {
            throw new FlowExecutionException(
                    FlowErrorCode.TF_GRAPH_INVALID,
                    "condition 节点缺少 branches: " + node.getId()
            );
        }

        for (int i = 0; i < branches.size(); i++) {
            JSONObject branch = branches.getJSONObject(i);
            if (branch == null) {
                continue;
            }
            if (!branchId.equals(branch.getString("id"))) {
                continue;
            }
            if (!ConditionBranchTargetSupport.hasBranchTarget(branch)) {
                // 无出口：正常结束，不抛「未配置目标」
                return null;
            }
            String target = branch.getString("target");
            if (!nodeById.containsKey(target)) {
                throw new FlowExecutionException(
                        FlowErrorCode.TF_GRAPH_INVALID,
                        "条件分支目标节点不存在: " + target
                );
            }
            return target;
        }

        throw new FlowExecutionException(
                FlowErrorCode.TF_BRANCH_UNWIRED,
                "未找到分支配置: " + branchId
        );
    }
}
