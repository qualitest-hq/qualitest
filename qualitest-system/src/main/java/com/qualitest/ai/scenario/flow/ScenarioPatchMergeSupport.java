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
     * inSeed 做键级浅合并；不再合并失败策略 / 条目级 maxSteps（已迁出场景）。
     */
    public static void applyScenarioUpdate(GraphRunScenario existing, GraphRunScenario update) {
        if (update.getName() != null && !update.getName().isBlank()) {
            existing.setName(update.getName());
        }
        if (update.getEnvId() != null) {
            existing.setEnvId(update.getEnvId());
        }
        if (update.getRemark() != null) {
            existing.setRemark(update.getRemark());
        }
        if (update.getInSeed() != null && !update.getInSeed().isEmpty()) {
            Map<String, Object> merged = new HashMap<>();
            if (existing.getInSeed() != null) {
                merged.putAll(existing.getInSeed());
            }
            merged.putAll(update.getInSeed());
            existing.setInSeed(merged);
        }
    }
}
