/**
 * Staging 确认后的视口导航：解析「下一个应聚焦的待确认单元」。
 *
 * 聚焦顺序规则：
 * - 确认 addNode 后，优先聚焦与其相连的 pending 连线（另一端已确认或非本 patch 新增）
 * - 否则按 patch 枚举序取下一个 pending 图单元
 * - 确认连线后，优先下一个 pending addNode，否则按枚举序
 */
import type { Edge } from '@vue-flow/core';

import type { AiStagingUnit } from '../types/aiStagingTypes';
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { isEdgeStagingKind, isGraphStagingKind, objectIdFromUnitId } from './stagingUnitIds';

/** 按 patch 内对象出现顺序枚举全部 Staging 单元 id */
export function enumeratePatchUnitIds(patch: FlowDesignPatch): string[] {
  const ids: string[] = [];
  for (const node of patch.addNodes ?? []) ids.push(`addNode:${node.id}`);
  for (const node of patch.updateNodes ?? []) ids.push(`updateNode:${node.id}`);
  for (const edge of patch.addEdges ?? []) ids.push(`addEdge:${edge.id}`);
  for (const edge of patch.updateEdges ?? []) ids.push(`updateEdge:${edge.id}`);
  for (const nodeId of patch.suggestedDeletes?.nodeIds ?? []) ids.push(`deleteNode:${nodeId}`);
  for (const edgeId of patch.suggestedDeletes?.edgeIds ?? []) ids.push(`deleteEdge:${edgeId}`);
  const meta = patch.scenarioPatch;
  for (const scenario of meta?.addScenarios ?? []) ids.push(`addScenario:${scenario.id}`);
  for (const scenario of meta?.updateScenarios ?? []) ids.push(`updateScenario:${scenario.id}`);
  for (const scenarioId of meta?.deleteScenarioIds ?? []) ids.push(`deleteScenario:${scenarioId}`);
  return ids;
}

/** 返回单元在 patch 枚举顺序中的下标，未知单元排到最后 */
function patchUnitOrder(patch: FlowDesignPatch, unitId: string): number {
  const idx = enumeratePatchUnitIds(patch).indexOf(unitId);
  return idx >= 0 ? idx : Number.MAX_SAFE_INTEGER;
}

/** 从 patch 或画布上查找连线的 source/target 端点 */
function getEdgeEndpoints(
  edgeId: string,
  patch: FlowDesignPatch,
  edges: Edge[],
): { source: string; target: string } | null {
  const fromPatch =
    patch.addEdges?.find((e) => e.id === edgeId) ?? patch.updateEdges?.find((e) => e.id === edgeId);
  if (fromPatch?.source && fromPatch?.target) {
    return { source: fromPatch.source, target: fromPatch.target };
  }
  const onCanvas = edges.find((e) => e.id === edgeId);
  return onCanvas ? { source: onCanvas.source, target: onCanvas.target } : null;
}

/** 是否为连线类单元（聚焦时需包含 source/target 节点） */
export function isEdgeFocusUnit(unit: Pick<AiStagingUnit, 'kind'>): boolean {
  return isEdgeStagingKind(unit.kind);
}

/**
 * 刚确认一个单元后，解析同消息下下一个应聚焦的 pending 图单元。
 *
 * 返回 null 表示本消息内已无 pending 图单元，调用方可退而查找全局 pending。
 * 不按端点依赖跳过；确认节点后优先相连 pending 边，否则按 patch 枚举序。
 */
export function resolveNextStagingFocusUnit(
  confirmedUnit: AiStagingUnit,
  patch: FlowDesignPatch,
  unitsForMessage: AiStagingUnit[],
  _confirmedUnitIds: ReadonlySet<string>,
  edges: Edge[],
): AiStagingUnit | null {
  const graphPending = unitsForMessage
    .filter(
      (u) =>
        u.messageId === confirmedUnit.messageId &&
        u.status === 'pending' &&
        isGraphStagingKind(u.kind),
    )
    .sort((a, b) => patchUnitOrder(patch, a.unitId) - patchUnitOrder(patch, b.unitId));

  if (!graphPending.length) return null;

  const confirmedNodeId =
    confirmedUnit.kind === 'addNode' ? objectIdFromUnitId(confirmedUnit.unitId) : null;

  if (confirmedUnit.kind === 'addNode' && confirmedNodeId) {
    const edgeUnits = graphPending.filter((u) => isEdgeFocusUnit(u));
    // 与刚确认节点相连的 pending 边（不要求另一端已确认）
    const bridgeEdge = edgeUnits.find((u) => {
      const edgeId = objectIdFromUnitId(u.unitId);
      const endpoints = getEdgeEndpoints(edgeId, patch, edges);
      if (!endpoints) return false;
      return endpoints.source === confirmedNodeId || endpoints.target === confirmedNodeId;
    });
    if (bridgeEdge) return bridgeEdge;

    const nextNode = graphPending.find((u) => u.kind === 'addNode');
    if (nextNode) return nextNode;

    if (edgeUnits.length) return edgeUnits[0];
  }

  if (confirmedUnit.kind === 'addEdge' || confirmedUnit.kind === 'updateEdge') {
    const nextNode = graphPending.find((u) => u.kind === 'addNode');
    if (nextNode) return nextNode;
  }

  return graphPending[0] ?? null;
}
