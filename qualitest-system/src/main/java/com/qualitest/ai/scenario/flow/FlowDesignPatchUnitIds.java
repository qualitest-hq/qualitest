package com.qualitest.ai.scenario.flow;

import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 AI patch 枚举 Staging 单元 id，与前端 buildStagingUnits 键名一致。
 */
public final class FlowDesignPatchUnitIds {

    private FlowDesignPatchUnitIds() {
    }

    public static Set<String> enumerate(FlowDesignPatch patch) {
        Set<String> unitIds = new LinkedHashSet<>();
        if (patch == null) {
            return unitIds;
        }
        addNodes(unitIds, patch.getAddNodes(), "addNode:");
        addNodes(unitIds, patch.getUpdateNodes(), "updateNode:");
        addEdges(unitIds, patch.getAddEdges(), "addEdge:");
        addEdges(unitIds, patch.getUpdateEdges(), "updateEdge:");

        FlowDesignPatch.SuggestedDeletes deletes = patch.getSuggestedDeletes();
        if (deletes != null) {
            addRawIds(unitIds, deletes.getNodeIds(), "deleteNode:");
            addRawIds(unitIds, deletes.getEdgeIds(), "deleteEdge:");
        }

        FlowDesignScenarioPatch scenarioPatch = patch.getScenarioPatch();
        if (scenarioPatch != null) {
            if (scenarioPatch.getActiveScenarioId() != null && !scenarioPatch.getActiveScenarioId().isBlank()) {
                unitIds.add("scenario:activeScenarioId");
            }
            addScenarios(unitIds, scenarioPatch.getAddScenarios(), "addScenario:");
            addScenarios(unitIds, scenarioPatch.getUpdateScenarios(), "updateScenario:");
            addRawIds(unitIds, scenarioPatch.getDeleteScenarioIds(), "deleteScenario:");
        }
        return unitIds;
    }

    /**
     * 当前 patch 是否仍有未确认/未拒绝的单元（不含本次正在确认的 unitId）。
     */
    public static boolean hasUnresolvedUnits(
            FlowDesignPatch patch,
            Set<String> confirmedUnitIds,
            Set<String> rejectedUnitIds,
            String confirmingUnitId) {
        Set<String> patchUnits = enumerate(patch);
        if (patchUnits.isEmpty()) {
            return false;
        }
        Set<String> resolved = new LinkedHashSet<>();
        if (confirmedUnitIds != null) {
            resolved.addAll(confirmedUnitIds);
        }
        if (rejectedUnitIds != null) {
            resolved.addAll(rejectedUnitIds);
        }
        if (confirmingUnitId != null && !confirmingUnitId.isBlank()) {
            resolved.add(confirmingUnitId.trim());
        }
        for (String unitId : patchUnits) {
            if (!resolved.contains(unitId)) {
                return true;
            }
        }
        return false;
    }

    private static void addNodes(Set<String> unitIds, List<GraphNode> nodes, String prefix) {
        if (nodes == null) {
            return;
        }
        for (GraphNode node : nodes) {
            if (node != null && node.getId() != null && !node.getId().isBlank()) {
                unitIds.add(prefix + node.getId().trim());
            }
        }
    }

    private static void addEdges(Set<String> unitIds, List<GraphEdge> edges, String prefix) {
        if (edges == null) {
            return;
        }
        for (GraphEdge edge : edges) {
            if (edge != null && edge.getId() != null && !edge.getId().isBlank()) {
                unitIds.add(prefix + edge.getId().trim());
            }
        }
    }

    private static void addScenarios(Set<String> unitIds, List<GraphRunScenario> scenarios, String prefix) {
        if (scenarios == null) {
            return;
        }
        for (GraphRunScenario scenario : scenarios) {
            if (scenario != null && scenario.getId() != null && !scenario.getId().isBlank()) {
                unitIds.add(prefix + scenario.getId().trim());
            }
        }
    }

    private static void addRawIds(Set<String> unitIds, List<String> ids, String prefix) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                unitIds.add(prefix + id.trim());
            }
        }
    }
}
