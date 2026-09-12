package com.qualitest.ai.tools;

import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.FlowDesignPatchUnitIds;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.FlowDesignScenarioPatch;
import com.qualitest.flow.model.GraphEdge;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphRunScenario;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 单轮造流中多次 submit_* 的结果容器（请求级，不落库）。
 * <p>
 * 行为：
 * <ul>
 *   <li>校验通过：单元并入累积 patch，供本轮结束时返回前端 Staging</li>
 *   <li>校验失败：只更新最近一次 validation，不改动已接受累积</li>
 *   <li>同一 unitId（如 addNode:n_1）再次成功：先删旧切片再写入，实现修正覆盖</li>
 * </ul>
 * 编排层据此判断 explainOnly（无任何成功单元）或返回累积 patch。
 */
@Getter
public class FlowDesignSubmitCapture {

    /** 本轮是否至少成功接受过一个 Staging 单元 */
    private boolean submitted;

    /** 已接受单元合并后的规范化 patch（可能含多个单元） */
    private FlowDesignPatch normalizedPatch;

    /** 最近一次 submit 的校验摘要（成功或失败都会更新） */
    private DesignValidationResult validation;

    /**
     * 记录一次单元 submit 的规范化结果。
     * 通过则合并；失败则仅刷新 validation。
     */
    public void record(FlowDesignPatchNormalizer.NormalizeResult result) {
        if (result == null) {
            return;
        }
        this.validation = result.validation();
        if (result.validation() != null && result.validation().isOk() && result.patch() != null) {
            this.submitted = true;
            this.normalizedPatch = mergeReplaceUnits(this.normalizedPatch, result.patch());
        }
    }

    /** 是否已有至少一个成功接受的单元（可返回画布 patch） */
    public boolean hasAccepted() {
        return submitted && normalizedPatch != null;
    }

    /**
     * 将本次 unitPatch 并入累计 base。
     * 先按 unitId 从 base 去掉同 id 切片，再追加本次内容；summary 以本次非空为准。
     */
    static FlowDesignPatch mergeReplaceUnits(FlowDesignPatch base, FlowDesignPatch unitPatch) {
        if (unitPatch == null) {
            return base;
        }
        Set<String> incoming = FlowDesignPatchUnitIds.enumerate(unitPatch);
        FlowDesignPatch acc = base != null ? copyPatch(base) : new FlowDesignPatch();
        if (!incoming.isEmpty() && base != null) {
            acc = removeUnits(acc, incoming);
        }
        appendAll(acc, unitPatch);
        if (unitPatch.getSummary() != null && !unitPatch.getSummary().isBlank()) {
            acc.setSummary(unitPatch.getSummary().trim());
        }
        return acc;
    }

    /** 深拷贝列表字段，避免原地改坏上一份累积。 */
    private static FlowDesignPatch copyPatch(FlowDesignPatch src) {
        FlowDesignPatch copy = new FlowDesignPatch();
        appendAll(copy, src);
        copy.setSummary(src.getSummary());
        return copy;
    }

    /** 把 src 各类变更列表追加到 target（不做去重；调用前已按 unitId 删过旧切片）。 */
    private static void appendAll(FlowDesignPatch target, FlowDesignPatch src) {
        if (src.getAddNodes() != null) {
            target.getAddNodes().addAll(src.getAddNodes());
        }
        if (src.getUpdateNodes() != null) {
            target.getUpdateNodes().addAll(src.getUpdateNodes());
        }
        if (src.getAddEdges() != null) {
            target.getAddEdges().addAll(src.getAddEdges());
        }
        if (src.getUpdateEdges() != null) {
            target.getUpdateEdges().addAll(src.getUpdateEdges());
        }
        if (src.getSuggestedDeletes() != null) {
            if (target.getSuggestedDeletes() == null) {
                target.setSuggestedDeletes(new FlowDesignPatch.SuggestedDeletes());
            }
            if (src.getSuggestedDeletes().getNodeIds() != null) {
                target.getSuggestedDeletes().getNodeIds().addAll(src.getSuggestedDeletes().getNodeIds());
            }
            if (src.getSuggestedDeletes().getEdgeIds() != null) {
                target.getSuggestedDeletes().getEdgeIds().addAll(src.getSuggestedDeletes().getEdgeIds());
            }
        }
        if (src.getScenarioPatch() != null) {
            FlowDesignScenarioPatch sp = src.getScenarioPatch();
            FlowDesignScenarioPatch targetSp = target.getScenarioPatch();
            if (targetSp == null) {
                targetSp = new FlowDesignScenarioPatch();
                target.setScenarioPatch(targetSp);
            }
            if (sp.getAddScenarios() != null) {
                targetSp.getAddScenarios().addAll(sp.getAddScenarios());
            }
            if (sp.getUpdateScenarios() != null) {
                targetSp.getUpdateScenarios().addAll(sp.getUpdateScenarios());
            }
            if (sp.getDeleteScenarioIds() != null) {
                if (targetSp.getDeleteScenarioIds() == null) {
                    targetSp.setDeleteScenarioIds(new ArrayList<>());
                }
                targetSp.getDeleteScenarioIds().addAll(sp.getDeleteScenarioIds());
            }
        }
    }

    /**
     * 从 patch 中去掉指定 unitId 对应的条目。
     * unitId 形如 addNode:{id}、deleteEdge:{id}、addScenario:{id} 等。
     */
    private static FlowDesignPatch removeUnits(FlowDesignPatch patch, Set<String> unitIds) {
        FlowDesignPatch filtered = new FlowDesignPatch();
        filtered.setSummary(patch.getSummary());
        filtered.setAddNodes(filterByUnit(patch.getAddNodes(), unitIds, "addNode:", GraphNode::getId));
        filtered.setUpdateNodes(filterByUnit(patch.getUpdateNodes(), unitIds, "updateNode:", GraphNode::getId));
        filtered.setAddEdges(filterByUnit(patch.getAddEdges(), unitIds, "addEdge:", GraphEdge::getId));
        filtered.setUpdateEdges(filterByUnit(patch.getUpdateEdges(), unitIds, "updateEdge:", GraphEdge::getId));
        if (patch.getSuggestedDeletes() != null) {
            FlowDesignPatch.SuggestedDeletes del = new FlowDesignPatch.SuggestedDeletes();
            del.setNodeIds(filterRawIds(patch.getSuggestedDeletes().getNodeIds(), unitIds, "deleteNode:"));
            del.setEdgeIds(filterRawIds(patch.getSuggestedDeletes().getEdgeIds(), unitIds, "deleteEdge:"));
            filtered.setSuggestedDeletes(del);
        }
        if (patch.getScenarioPatch() != null) {
            FlowDesignScenarioPatch sp = patch.getScenarioPatch();
            FlowDesignScenarioPatch out = new FlowDesignScenarioPatch();
            out.setAddScenarios(filterByUnit(sp.getAddScenarios(), unitIds, "addScenario:", GraphRunScenario::getId));
            out.setUpdateScenarios(filterByUnit(sp.getUpdateScenarios(), unitIds, "updateScenario:", GraphRunScenario::getId));
            out.setDeleteScenarioIds(filterRawIds(sp.getDeleteScenarioIds(), unitIds, "deleteScenario:"));
            if (FlowDesignScenarioPatch.hasChanges(out)) {
                filtered.setScenarioPatch(out);
            }
        }
        return filtered;
    }

    /** 保留 unitId 不在待删集合中的对象条目。 */
    private static <T> List<T> filterByUnit(List<T> items, Set<String> unitIds, String prefix,
                                            Function<T, String> idFn) {
        if (items == null || items.isEmpty()) {
            return items == null ? null : new ArrayList<>();
        }
        List<T> kept = new ArrayList<>();
        for (T item : items) {
            if (item == null) {
                continue;
            }
            String id = idFn.apply(item);
            if (id != null && !unitIds.contains(prefix + id.trim())) {
                kept.add(item);
            }
        }
        return kept;
    }

    /** 保留 unitId 不在待删集合中的纯 id 列表（删除节点/边/场景）。 */
    private static List<String> filterRawIds(List<String> ids, Set<String> unitIds, String prefix) {
        if (ids == null) {
            return null;
        }
        List<String> kept = new ArrayList<>();
        for (String id : ids) {
            if (id != null && !unitIds.contains(prefix + id.trim())) {
                kept.add(id);
            }
        }
        return kept;
    }
}
