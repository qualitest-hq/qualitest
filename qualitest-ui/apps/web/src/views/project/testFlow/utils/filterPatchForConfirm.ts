/**
 * 确认请求前裁剪 patch，减小载荷。
 * 只保留 keepUnitIds 中的单元（通常为仍 pending 的单元，外加当前正在确认的那一项）。
 */
import type { FlowDesignPatch, FlowDesignScenarioPatch } from '../types/aiDesignTypes';
import { enumeratePatchUnitIds } from './stagingFocusNavigation';

/**
 * 按 unitId 白名单过滤 patch 各列表；白名单为空时只保留 summary。
 */
export function filterPatchForConfirm(
  patch: FlowDesignPatch,
  keepUnitIds: ReadonlySet<string>,
): FlowDesignPatch {
  if (!keepUnitIds.size) {
    return { summary: patch.summary };
  }

  const addNodes = filterById(patch.addNodes, 'addNode:', keepUnitIds);
  const updateNodes = filterById(patch.updateNodes, 'updateNode:', keepUnitIds);
  const addEdges = filterById(patch.addEdges, 'addEdge:', keepUnitIds);
  const updateEdges = filterById(patch.updateEdges, 'updateEdge:', keepUnitIds);

  const nodeIds = (patch.suggestedDeletes?.nodeIds ?? []).filter((id) =>
    keepUnitIds.has(`deleteNode:${id}`),
  );
  const edgeIds = (patch.suggestedDeletes?.edgeIds ?? []).filter((id) =>
    keepUnitIds.has(`deleteEdge:${id}`),
  );

  let scenarioPatch: FlowDesignScenarioPatch | undefined;
  const sp = patch.scenarioPatch;
  if (sp) {
    const addScenarios = filterById(sp.addScenarios, 'addScenario:', keepUnitIds);
    const updateScenarios = filterById(sp.updateScenarios, 'updateScenario:', keepUnitIds);
    const deleteScenarioIds = (sp.deleteScenarioIds ?? []).filter((id) =>
      keepUnitIds.has(`deleteScenario:${id}`),
    );
    if (addScenarios?.length || updateScenarios?.length || deleteScenarioIds.length) {
      scenarioPatch = {
        addScenarios,
        updateScenarios,
        deleteScenarioIds: deleteScenarioIds.length ? deleteScenarioIds : undefined,
      };
    }
  }

  return {
    summary: patch.summary,
    addNodes,
    updateNodes,
    addEdges,
    updateEdges,
    suggestedDeletes:
      nodeIds.length || edgeIds.length
        ? {
            nodeIds: nodeIds.length ? nodeIds : undefined,
            edgeIds: edgeIds.length ? edgeIds : undefined,
          }
        : undefined,
    scenarioPatch,
  };
}

/**
 * 确认请求用：保留当前 unitId，以及尚未确认、尚未拒绝的其它单元。
 */
export function slimPatchKeepingUnresolved(
  patch: FlowDesignPatch,
  unitId: string,
  confirmed: ReadonlySet<string>,
  rejected: ReadonlySet<string>,
): FlowDesignPatch {
  const keep = new Set<string>([unitId]);
  for (const id of enumeratePatchUnitIds(patch)) {
    if (!confirmed.has(id) && !rejected.has(id)) {
      keep.add(id);
    }
  }
  return filterPatchForConfirm(patch, keep);
}

/** 按 prefix+id 是否在 keep 集合中过滤带 id 的列表 */
function filterById<T extends { id?: string }>(
  items: T[] | undefined,
  prefix: string,
  keep: ReadonlySet<string>,
): T[] | undefined {
  if (!items?.length) return undefined;
  const out = items.filter((item) => item?.id && keep.has(prefix + item.id));
  return out.length ? out : undefined;
}
