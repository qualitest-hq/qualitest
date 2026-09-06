package com.qualitest.flow.run;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.exception.FlowErrorCode;
import com.qualitest.flow.exception.FlowExecutionException;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.node.StepResult;
import com.qualitest.flow.graph.ConditionBranchTerminalSupport;
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
 *   <li>普通节点（http/assert/delay/assign/script/subflow/input）：必须仅有一条出边，沿该边 target 前进</li>
 *   <li>condition 节点：读取本步 {@link com.qualitest.flow.node.StepResult#getBranchTaken()}，
 *       在 {@code data.branches} 中匹配 branchId 后取 target</li>
 * </ul>
 */
public class GraphWalker {

    /** 单次 Run 允许的最大步骤数，超出由执行器终止并抛 TF_RUN_STEP_LIMIT */
    public static final int DEFAULT_MAX_STEPS = 50;

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
     * 根据当前节点与步骤结果解析下一节点 id；无后继时返回 null。
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
                    "当前节点存在多条出边，仅 condition 支持多出口: " + node.getId()
            );
        }
        return outEdges.get(0).getTarget();
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
     * 从 condition 步骤结果的 branchTaken 反查 branches[].target。
     * target 为空、节点不存在或 branchId 无匹配时抛 FlowExecutionException；
     * {@code terminal} 分支返回 null 表示流程结束。
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
            if (ConditionBranchTerminalSupport.isTerminalBranch(branch)) {
                return null;
            }
            String target = branch.getString("target");
            if (target == null || target.isBlank()) {
                throw new FlowExecutionException(
                        FlowErrorCode.TF_BRANCH_UNWIRED,
                        "条件分支未配置目标: " + branchId
                );
            }
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
