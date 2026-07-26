package com.qualitest.ai.tools;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;

/**
 * FlowDesignPatch 变更数量统计。
 * <p>
 * 在 {@code submit_flow_design_patch} 校验通过时写入工具响应的 {@code patchStats}，
 * 也可由编排层写入会话 meta，便于前端展示本次建议的规模。
 */
public final class FlowDesignPatchStats {

    private FlowDesignPatchStats() {
    }

    /**
     * 统计 patch 中各类变更条数。
     *
     * @return JSON 对象，可能含 addNodes、updateNodes、addEdges、updateEdges、
     *         deleteNodes、deleteEdges、scenarioPatch 等键；patch 为 null 时返回空对象
     */
    public static JSONObject build(FlowDesignPatch patch) {
        JSONObject stats = new JSONObject();
        if (patch == null) {
            return stats;
        }
        if (patch.getAddNodes() != null) {
            stats.put("addNodes", patch.getAddNodes().size());
        }
        if (patch.getUpdateNodes() != null) {
            stats.put("updateNodes", patch.getUpdateNodes().size());
        }
        if (patch.getAddEdges() != null) {
            stats.put("addEdges", patch.getAddEdges().size());
        }
        if (patch.getUpdateEdges() != null) {
            stats.put("updateEdges", patch.getUpdateEdges().size());
        }
        if (patch.getSuggestedDeletes() != null) {
            var d = patch.getSuggestedDeletes();
            int deleteNodes = d.getNodeIds() != null ? d.getNodeIds().size() : 0;
            int deleteEdges = d.getEdgeIds() != null ? d.getEdgeIds().size() : 0;
            stats.put("deleteNodes", deleteNodes);
            stats.put("deleteEdges", deleteEdges);
        }
        FlowDesignScenarioPatch scenarioPatch = patch.getScenarioPatch();
        if (scenarioPatch != null && FlowDesignScenarioPatch.hasChanges(scenarioPatch)) {
            stats.put("scenarioPatch", true);
        }
        return stats;
    }
}
