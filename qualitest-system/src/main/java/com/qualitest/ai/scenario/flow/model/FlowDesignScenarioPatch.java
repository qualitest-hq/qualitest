package com.qualitest.ai.scenario.flow.model;

import com.qualitest.flow.model.GraphRunScenario;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对测试流运行场景配置（graph_json.meta）的增量修改建议。
 * <p>
 * 用于新增/修改/删除运行场景，或切换画布默认运行场景。
 */
@Getter
@Setter
public class FlowDesignScenarioPatch {

    /**
     * 切换默认运行场景 id；对应 meta.activeScenarioId
     */
    private String activeScenarioId;

    /**
     * 新增运行场景列表
     */
    private List<GraphRunScenario> addScenarios = new ArrayList<>();

    /**
     * 按 id 修改已有运行场景（浅合并 name、testProjectEnvId、flowSeed、remark）
     */
    private List<GraphRunScenario> updateScenarios = new ArrayList<>();

    /**
     * 建议删除的运行场景 id 列表
     */
    private List<String> deleteScenarioIds = new ArrayList<>();

    /**
     * 判断 patch 是否包含实质运行场景变更。
     */
    public static boolean hasChanges(FlowDesignScenarioPatch patch) {
        if (patch == null) {
            return false;
        }
        if (patch.getActiveScenarioId() != null && !patch.getActiveScenarioId().isBlank()) {
            return true;
        }
        if (patch.getAddScenarios() != null && !patch.getAddScenarios().isEmpty()) {
            return true;
        }
        if (patch.getUpdateScenarios() != null && !patch.getUpdateScenarios().isEmpty()) {
            return true;
        }
        return patch.getDeleteScenarioIds() != null && !patch.getDeleteScenarioIds().isEmpty();
    }
}
