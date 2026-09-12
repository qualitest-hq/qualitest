/**
 * 灌入 Staging 前过滤非法 patch 项。
 * 缺 id、未知/禁止的节点 type、update/delete 目标不在画布上的项不建 unit，并收集 warnings 供 toast。
 * 返回新 patch 对象，不修改入参。
 */
import { isKnownNodeType } from '@/utils/flow/nodeTypes';
import type { GraphEdge, GraphNode, GraphRunScenario } from '@/utils/flow/graphTypes';

import type { FlowDesignPatch } from '../types/aiDesignTypes';

/** 当前画布上已有对象 id，用于判断 update/delete 目标是否存在 */
export interface ValidatePatchForHydrateContext {
  /** 画布已有节点 id */
  existingNodeIds?: ReadonlySet<string>;
  /** 画布已有边 id */
  existingEdgeIds?: ReadonlySet<string>;
  /** 画布已有运行场景 id */
  existingScenarioIds?: ReadonlySet<string>;
}

/** 过滤结果：可用的 patch + 跳过说明 */
export interface ValidatePatchForHydrateResult {
  /** 过滤后可灌入 Staging 的 patch */
  patch: FlowDesignPatch;
  /** 被跳过项的说明文案 */
  warnings: string[];
}

/**
 * 按规则过滤 add/update/delete 节点、边、场景。
 * addNode 必须有 id 与合法 type（禁止 input）；缺 type 直接跳过，不默认成 http。
 */
export function validatePatchForHydrate(
  patch: FlowDesignPatch,
  ctx: ValidatePatchForHydrateContext = {},
): ValidatePatchForHydrateResult {
  const warnings: string[] = [];
  const existingNodes = ctx.existingNodeIds ?? new Set<string>();
  const existingEdges = ctx.existingEdgeIds ?? new Set<string>();
  const existingScenarios = ctx.existingScenarioIds ?? new Set<string>();

  const addNodeIds = new Set<string>();
  const addNodes: GraphNode[] = [];
  for (const node of patch.addNodes ?? []) {
    if (!node?.id?.trim()) {
      warnings.push('已跳过缺 id 的 addNode');
      continue;
    }
    const id = node.id.trim();
    const type = typeof node.type === 'string' ? node.type.trim() : '';
    if (!type) {
      warnings.push(`addNode:${id} 缺 type，已跳过（不再默认当成 http）`);
      continue;
    }
    if (!isKnownNodeType(type) || type === 'input') {
      warnings.push(`addNode:${id} 类型「${type}」非法或禁止 AI 造，已跳过`);
      continue;
    }
    addNodeIds.add(id);
    addNodes.push(node);
  }

  const updateNodes: GraphNode[] = [];
  for (const node of patch.updateNodes ?? []) {
    if (!node?.id?.trim()) {
      warnings.push('已跳过缺 id 的 updateNode');
      continue;
    }
    const id = node.id.trim();
    if (!existingNodes.has(id) && !addNodeIds.has(id)) {
      warnings.push(`updateNode:${id} 目标不在画布，已跳过`);
      continue;
    }
    const type = typeof node.type === 'string' ? node.type.trim() : '';
    if (type && (!isKnownNodeType(type) || type === 'input')) {
      warnings.push(`updateNode:${id} 类型「${type}」非法，已跳过`);
      continue;
    }
    updateNodes.push(node);
  }

  const addEdges: GraphEdge[] = [];
  for (const edge of patch.addEdges ?? []) {
    if (!edge?.id?.trim()) {
      warnings.push('已跳过缺 id 的 addEdge');
      continue;
    }
    addEdges.push(edge);
  }

  const updateEdges: GraphEdge[] = [];
  for (const edge of patch.updateEdges ?? []) {
    if (!edge?.id?.trim()) {
      warnings.push('已跳过缺 id 的 updateEdge');
      continue;
    }
    const id = edge.id.trim();
    if (!existingEdges.has(id)) {
      warnings.push(`updateEdge:${id} 目标不在画布，已跳过`);
      continue;
    }
    updateEdges.push(edge);
  }

  const nodeIds = (patch.suggestedDeletes?.nodeIds ?? []).filter((id) => {
    if (!id?.trim()) return false;
    if (!existingNodes.has(id.trim())) {
      warnings.push(`deleteNode:${id} 目标不在画布，已跳过`);
      return false;
    }
    return true;
  });
  const edgeIds = (patch.suggestedDeletes?.edgeIds ?? []).filter((id) => {
    if (!id?.trim()) return false;
    if (!existingEdges.has(id.trim())) {
      warnings.push(`deleteEdge:${id} 目标不在画布，已跳过`);
      return false;
    }
    return true;
  });

  const sp = patch.scenarioPatch;
  let scenarioPatch = sp;
  if (sp) {
    const addScenarios: GraphRunScenario[] = [];
    for (const s of sp.addScenarios ?? []) {
      if (!s?.id?.trim()) {
        warnings.push('已跳过缺 id 的 addScenario');
        continue;
      }
      addScenarios.push(s);
    }
    const updateScenarios: GraphRunScenario[] = [];
    for (const s of sp.updateScenarios ?? []) {
      if (!s?.id?.trim()) {
        warnings.push('已跳过缺 id 的 updateScenario');
        continue;
      }
      const id = s.id.trim();
      if (!existingScenarios.has(id)) {
        warnings.push(`updateScenario:${id} 目标不在画布，已跳过`);
        continue;
      }
      updateScenarios.push(s);
    }
    const deleteScenarioIds = (sp.deleteScenarioIds ?? []).filter((id) => {
      if (!id?.trim()) return false;
      if (!existingScenarios.has(id.trim())) {
        warnings.push(`deleteScenario:${id} 目标不在画布，已跳过`);
        return false;
      }
      return true;
    });
    scenarioPatch = {
      ...sp,
      addScenarios: addScenarios.length ? addScenarios : undefined,
      updateScenarios: updateScenarios.length ? updateScenarios : undefined,
      deleteScenarioIds: deleteScenarioIds.length ? deleteScenarioIds : undefined,
    };
    if (!scenarioPatch.addScenarios && !scenarioPatch.updateScenarios && !scenarioPatch.deleteScenarioIds) {
      scenarioPatch = undefined;
    }
  }

  return {
    patch: {
      ...patch,
      addNodes: addNodes.length ? addNodes : undefined,
      updateNodes: updateNodes.length ? updateNodes : undefined,
      addEdges: addEdges.length ? addEdges : undefined,
      updateEdges: updateEdges.length ? updateEdges : undefined,
      suggestedDeletes:
        nodeIds.length || edgeIds.length
          ? { nodeIds: nodeIds.length ? nodeIds : undefined, edgeIds: edgeIds.length ? edgeIds : undefined }
          : undefined,
      scenarioPatch,
    },
    warnings,
  };
}
