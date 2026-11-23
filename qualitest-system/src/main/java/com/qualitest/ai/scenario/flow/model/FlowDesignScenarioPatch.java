package com.qualitest.ai.scenario.flow.model;

import com.qualitest.flow.model.GraphRunScenario;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对运行场景配置的增量修改建议。
 * <p>
 * 目标为 {@code scenario-entry.data.scenarios}。
 * 不包含切换画布默认场景（由用户在界面直接改）。
 */
@Getter
@Setter
public class FlowDesignScenarioPatch {

    /**
     * 目标 scenario-entry 节点 id；空则取图上第一个场景入口，无则新建。
     */
    private String entryNodeId;

    /** 新增运行场景列表 */
    private List<GraphRunScenario> addScenarios = new ArrayList<>();

    /**
     * 按 id 修改已有运行场景（浅合并 name、envId/inSeed 等）
     */
    private List<GraphRunScenario> updateScenarios = new ArrayList<>();

    /** 建议删除的运行场景 id 列表 */
    private List<String> deleteScenarioIds = new ArrayList<>();

    /** 判断 patch 是否包含实质运行场景变更 */
    public static boolean hasChanges(FlowDesignScenarioPatch patch) {
        if (patch == null) {
            return false;
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
