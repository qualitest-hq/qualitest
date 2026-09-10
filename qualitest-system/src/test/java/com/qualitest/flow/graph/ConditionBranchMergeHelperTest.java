package com.qualitest.flow.graph;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 测 ConditionBranchMergeHelper：patch 合并写入边时同步 condition 节点 branches[].target。
 * 边界：纯函数，无真实图执行。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ConditionBranchMergeHelperTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConditionBranchMergeHelperTest {

    /**
     * 前提：condition 出边新增，IF/ELSE 均尚未绑定 target。
     * 期望：首条未绑定分支（IF）写入 edge.target；ELSE 仍为 null。
     */
    @Test
    @Order(1)
    @DisplayName("新增出边时绑定首个未绑定分支")
    void syncConditionEdgeToNodes_bindsFirstUnboundBranch() {
        String ifBranchId = "2071158532992012288";
        GraphNode condition = conditionNode("1001", ifBranchId, "2071158532992012289");
        GraphNode http = GraphNode.builder().id("1002").type("http").build();
        List<GraphNode> nodes = new ArrayList<>(List.of(condition, http));
        List<GraphEdge> edges = new ArrayList<>();
        GraphEdge edge = GraphEdge.builder().id("2001").source("1001").target("1002").build();

        ConditionBranchMergeHelper.syncConditionEdgeToNodes(nodes, edges, edge);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) nodes.get(0).getData().get("branches");
        assertEquals("1002", branches.get(0).get("target"));
        assertNull(branches.get(1).get("target"));
    }

    /**
     * 前提：IF 分支已绑定 target=1002，即将删除对应出边。
     * 期望：branches[].target 被清除。
     */
    @Test
    @Order(2)
    @DisplayName("删除出边时清除对应分支 target")
    void clearConditionTargetForRemovedEdge_clearsBinding() {
        String ifBranchId = "2071158532992012288";
        GraphNode condition = conditionNode("1001", ifBranchId, "2071158532992012289");
        Map<String, Object> data = condition.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) data.get("branches");
        branches.get(0).put("target", "1002");

        List<GraphNode> nodes = new ArrayList<>(List.of(condition));
        GraphEdge edge = GraphEdge.builder().id("2001").source("1001").target("1002").build();

        ConditionBranchMergeHelper.clearConditionTargetForRemovedEdge(nodes, edge);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> after = (List<Map<String, Object>>) nodes.get(0).getData().get("branches");
        assertNull(after.get(0).get("target"));
    }

    /**
     * 前提：IF 原指向 1003（边 2000），再同步新出边到 1002。
     * 期望：旧边 2000 删除；仅留 2001；IF target 更新为 1002。
     */
    @Test
    @Order(3)
    @DisplayName("重绑分支时删除旧出边并更新 target")
    void syncConditionEdgeToNodes_removesStaleEdgeWhenRebinding() {
        String ifBranchId = "2071158532992012288";
        GraphNode condition = conditionNode("1001", ifBranchId, "2071158532992012289");
        Map<String, Object> data = condition.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) data.get("branches");
        branches.get(0).put("target", "1003");

        GraphNode http2 = GraphNode.builder().id("1002").type("http").build();
        GraphNode http3 = GraphNode.builder().id("1003").type("http").build();
        List<GraphNode> nodes = new ArrayList<>(List.of(condition, http2, http3));
        List<GraphEdge> edges = new ArrayList<>();
        edges.add(GraphEdge.builder().id("2000").source("1001").target("1003").build());

        GraphEdge newEdge = GraphEdge.builder().id("2001").source("1001").target("1002").build();
        edges.add(newEdge);
        ConditionBranchMergeHelper.syncConditionEdgeToNodes(nodes, edges, newEdge);

        assertEquals(1, edges.size());
        assertEquals("2001", edges.get(0).getId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> after = (List<Map<String, Object>>) nodes.get(0).getData().get("branches");
        assertEquals("1002", after.get(0).get("target"));
    }

    /**
     * 前提：IF 分支带旧字段 terminal=true 且无 target；显式指定 branchId 绑定新 target。
     * 期望：写入 target，并删除 terminal 字段。
     */
    @Test
    @Order(4)
    @DisplayName("为 terminal 分支连线时清除 terminal 并写入 target")
    void bindConditionBranchTarget_clearsTerminalWhenBindingTarget() {
        String ifBranchId = "2071158532992012288";
        GraphNode condition = conditionNode("1001", ifBranchId, "2071158532992012289");
        Map<String, Object> data = condition.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) data.get("branches");
        branches.get(0).put("terminal", true);

        boolean changed = ConditionBranchMergeHelper.bindConditionBranchTarget(
                data, "1002", ifBranchId);

        assertEquals(true, changed);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> after = (List<Map<String, Object>>) data.get("branches");
        assertEquals("1002", after.get(0).get("target"));
        assertFalse(after.get(0).containsKey("terminal"));
    }

    /** 构造带 IF/ELSE 两条分支、均未绑定 target 的 condition 节点 */
    private static GraphNode conditionNode(String id, String ifBranchId, String elseBranchId) {
        Map<String, Object> ifBranch = new HashMap<>();
        ifBranch.put("id", ifBranchId);
        ifBranch.put("kind", "if");
        ifBranch.put("conditions", List.of(Map.of("left", "flow.code", "operator", "eq", "right", "0")));

        Map<String, Object> elseBranch = new HashMap<>();
        elseBranch.put("id", elseBranchId);
        elseBranch.put("kind", "else");
        elseBranch.put("conditions", List.of());

        Map<String, Object> data = new HashMap<>();
        data.put("name", "条件");
        data.put("branches", new ArrayList<>(List.of(ifBranch, elseBranch)));

        return GraphNode.builder().id(id).type("condition").data(data).build();
    }
}
