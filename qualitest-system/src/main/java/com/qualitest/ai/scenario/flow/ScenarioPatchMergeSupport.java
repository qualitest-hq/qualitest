package com.qualitest.ai.scenario.flow;

import com.qualitest.flow.model.GraphRunScenario;

import java.util.HashMap;
import java.util.Map;

/**
 * 运行场景 update 时的字段合并逻辑。
 *
 * 将 patch 中的非空字段写入已有场景对象，供批量合并与单单元 confirm 共用，
 * 避免两处各自实现导致字段遗漏。
 */
public final class ScenarioPatchMergeSupport {

    private ScenarioPatchMergeSupport() {
    }

    /**
     * 把 update 里的非空字段覆盖到已有场景。
     * flowSeed 做键级浅合并；remark、失败策略等字段同样在此统一写入。
     */
    public static void applyScenarioUpdate(GraphRunScenario existing, GraphRunScenario update) {
        if (update.getName() != null && !update.getName().isBlank()) {
            existing.setName(update.getName());
        }
        if (update.getTestProjectEnvId() != null) {
            existing.setTestProjectEnvId(update.getTestProjectEnvId());
        }
        if (update.getRemark() != null) {
            existing.setRemark(update.getRemark());
        }
        if (update.getOnNodeFailure() != null && !update.getOnNodeFailure().isBlank()) {
            existing.setOnNodeFailure(update.getOnNodeFailure());
        }
        if (update.getOnSnapshotFailure() != null && !update.getOnSnapshotFailure().isBlank()) {
            existing.setOnSnapshotFailure(update.getOnSnapshotFailure());
        }
        if (update.getFlowSeed() != null && !update.getFlowSeed().isEmpty()) {
            Map<String, Object> merged = new HashMap<>();
            if (existing.getFlowSeed() != null) {
                merged.putAll(existing.getFlowSeed());
            }
            merged.putAll(update.getFlowSeed());
            existing.setFlowSeed(merged);
        }
    }
}
