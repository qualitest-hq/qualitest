package com.qualitest.ai.scenario.flow;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * scenario-entry.data.scenarios 增量 patch 的合并器。
 * <p>
 * 在内存中操作场景列表副本，不写库。支持：
 * <ul>
 *   <li>新增场景（跳过已存在 id）</li>
 *   <li>按 id 更新 name、环境、inSeed、remark、失败策略等</li>
 *   <li>删除场景（至少保留一条；删光时回滚到原列表）</li>
 * </ul>
 */
public final class ScenarioPatchMerger {

    private ScenarioPatchMerger() {
    }

    /**
     * 将 scenarioPatch 合并到场景列表副本。
     *
     * @param base     基准场景列表，null 时创建含默认冒烟场景
     * @param patch    AI 返回的场景增量，无变更时直接返回克隆
     * @param warnings 合并过程产生的非阻断提示（如删光场景时回滚）
     * @return 合并后的列表副本，不修改入参 base
     */
    public static List<GraphRunScenario> merge(List<GraphRunScenario> base,
                                               FlowDesignScenarioPatch patch,
                                               List<String> warnings) {
        List<GraphRunScenario> scenarios = cloneScenarios(base);
        if (patch == null || !FlowDesignScenarioPatch.hasChanges(patch)) {
            return scenarios;
        }

        if (patch.getAddScenarios() != null) {
            for (GraphRunScenario scenario : patch.getAddScenarios()) {
                if (scenario == null || scenario.getId() == null || scenario.getId().isBlank()) {
                    continue;
                }
                if (findScenario(scenarios, scenario.getId()) != null) {
                    continue;
                }
                scenarios.add(scenario);
            }
        }

        if (patch.getUpdateScenarios() != null) {
            for (GraphRunScenario update : patch.getUpdateScenarios()) {
                if (update == null || update.getId() == null || update.getId().isBlank()) {
                    continue;
                }
                GraphRunScenario existing = findScenario(scenarios, update.getId());
                if (existing == null) {
                    continue;
                }
                ScenarioPatchMergeSupport.applyScenarioUpdate(existing, update);
            }
        }

        if (patch.getDeleteScenarioIds() != null && !patch.getDeleteScenarioIds().isEmpty()) {
            Set<String> deleteIds = new HashSet<>(patch.getDeleteScenarioIds());
            int before = scenarios.size();
            scenarios.removeIf(s -> s != null && deleteIds.contains(s.getId()));
            if (scenarios.isEmpty()) {
                warnings.add("删除场景后列表为空，已保留原场景列表");
                scenarios = cloneScenarios(base);
            } else if (scenarios.size() < before) {
                // 删场景后列表非空，无需维护 meta 级 activeScenarioId
            }
        }

        return scenarios;
    }

    private static GraphRunScenario findScenario(List<GraphRunScenario> scenarios, String id) {
        if (scenarios == null || id == null) {
            return null;
        }
        for (GraphRunScenario scenario : scenarios) {
            if (scenario != null && id.equals(scenario.getId())) {
                return scenario;
            }
        }
        return null;
    }

    /**
     * 深拷贝场景列表。
     * base 为 null 或空时生成一条默认「冒烟」场景。
     */
    static List<GraphRunScenario> cloneScenarios(List<GraphRunScenario> base) {
        if (base == null || base.isEmpty()) {
            GraphRunScenario defaultScenario = GraphRunScenario.builder()
                    .id(String.valueOf(IdUtil.getSnowflakeNextId()))
                    .name("默认（冒烟）")
                    .envId("")
                    .inSeed(new HashMap<>())
                    .remark("")
                    .build();
            return new ArrayList<>(List.of(defaultScenario));
        }
        return JSON.parseArray(JSON.toJSONString(base), GraphRunScenario.class);
    }
}