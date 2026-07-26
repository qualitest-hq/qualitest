package com.qualitest.ai.scenario.flow;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphRunScenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运行场景（meta.scenarios）增量 patch 的合并器。
 * <p>
 * 在内存中操作 {@link GraphMeta} 副本，不写库。支持：
 * <ul>
 *   <li>新增场景（跳过已存在 id）</li>
 *   <li>按 id 更新 name、环境、flowSeed、remark、失败策略等</li>
 *   <li>删除场景（至少保留一条；删光时回滚到原列表）</li>
 *   <li>切换 activeScenarioId（目标不存在时写入 warning 并忽略）</li>
 * </ul>
 */
public final class ScenarioPatchMerger {

    private ScenarioPatchMerger() {
    }

    /**
     * 将 scenarioPatch 合并到 meta 的场景配置副本。
     *
     * @param base     基准 meta，null 时创建含默认冒烟场景的 meta
     * @param patch    AI 返回的场景增量，无变更时直接返回克隆
     * @param warnings 合并过程产生的非阻断提示（如 activeScenarioId 无效）
     * @return 合并后的 meta 副本，不修改入参 base
     */
    public static GraphMeta merge(GraphMeta base, FlowDesignScenarioPatch patch, List<String> warnings) {
        GraphMeta meta = cloneScenarioMeta(base);
        if (patch == null || !FlowDesignScenarioPatch.hasChanges(patch)) {
            return meta;
        }

        // 追加新场景，id 已存在则跳过
        if (patch.getAddScenarios() != null) {
            for (GraphRunScenario scenario : patch.getAddScenarios()) {
                if (scenario == null || scenario.getId() == null || scenario.getId().isBlank()) {
                    continue;
                }
                if (findScenario(meta.getScenarios(), scenario.getId()) != null) {
                    continue;
                }
                meta.getScenarios().add(scenario);
            }
        }

        // 按 id 合并场景字段（name、环境、备注、失败策略、flowSeed 等）
        if (patch.getUpdateScenarios() != null) {
            for (GraphRunScenario update : patch.getUpdateScenarios()) {
                if (update == null || update.getId() == null || update.getId().isBlank()) {
                    continue;
                }
                GraphRunScenario existing = findScenario(meta.getScenarios(), update.getId());
                if (existing == null) {
                    continue;
                }
                ScenarioPatchMergeSupport.applyScenarioUpdate(existing, update);
            }
        }

        // 删除场景：不允许删光；若当前 active 被删则切到剩余首条
        if (patch.getDeleteScenarioIds() != null && !patch.getDeleteScenarioIds().isEmpty()) {
            Set<String> deleteIds = new HashSet<>(patch.getDeleteScenarioIds());
            int before = meta.getScenarios().size();
            meta.getScenarios().removeIf(s -> s != null && deleteIds.contains(s.getId()));
            if (meta.getScenarios().isEmpty()) {
                warnings.add("删除场景后列表为空，已保留原场景列表");
                meta = cloneScenarioMeta(base);
            } else if (meta.getScenarios().size() < before) {
                String activeId = meta.getActiveScenarioId();
                if (activeId != null && deleteIds.contains(activeId)) {
                    meta.setActiveScenarioId(meta.getScenarios().get(0).getId());
                }
            }
        }

        // 切换默认运行场景
        if (patch.getActiveScenarioId() != null && !patch.getActiveScenarioId().isBlank()) {
            String targetId = patch.getActiveScenarioId().trim();
            if (findScenario(meta.getScenarios(), targetId) != null) {
                meta.setActiveScenarioId(targetId);
            } else {
                warnings.add("activeScenarioId 不存在，已忽略：" + targetId);
            }
        }

        return meta;
    }

    /** 在场景列表中按 id 查找 */
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
     * 深拷贝 meta 的场景相关字段。
     * base 为 null 时生成一条默认「冒烟」场景作为初始配置。
     */
    static GraphMeta cloneScenarioMeta(GraphMeta base) {
        if (base == null) {
            GraphRunScenario defaultScenario = GraphRunScenario.builder()
                    .id(String.valueOf(IdUtil.getSnowflakeNextId()))
                    .name("默认（冒烟）")
                    .testProjectEnvId("")
                    .flowSeed(new HashMap<>())
                    .remark("")
                    .build();
            return GraphMeta.builder()
                    .activeScenarioId(defaultScenario.getId())
                    .scenarios(new ArrayList<>(List.of(defaultScenario)))
                    .flowOutputs(new ArrayList<>())
                    .build();
        }
        GraphMeta cloned = JSON.parseObject(JSON.toJSONString(base), GraphMeta.class);
        if (cloned.getScenarios() == null) {
            cloned.setScenarios(new ArrayList<>());
        }
        if (cloned.getFlowOutputs() == null) {
            cloned.setFlowOutputs(new ArrayList<>());
        }
        return cloned;
    }
}
