package com.qualitest.flow.graph;

import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ConditionBranchMergeHelper} 单元测试：验证 condition 出边与 branches[].target 的同步。
 * <p>
 * 被测对象在 patch 合并写入 addEdge / updateEdge / deleteEdge 时，
 * 维护源 condition 节点 data.branches[].target，供运行期 {@link com.qualitest.flow.run.GraphWalker} 解析下一跳。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ConditionBranchMergeHelperTest
 */
class ConditionBranchMergeHelperTest {

    /**
     * 新增 condition 出边且分支尚未绑定 target。
     * 期望：首条未绑定分支（IF）写入 edge.target；ELSE 分支仍为 null。
     */
    @Test
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
     * 删除 condition 出边前清除分支绑定。
     * 期望：指向该 target 的 branches[].target 被移除。
     */
    @Test
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
     * 分支改连到新 target 时，移除同源到旧 target 的冗余出边。
     * 期望：旧出边 2000 被删；仅保留新出边 2001；IF 分支 target 更新为 1002。
     */
    @Test
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
