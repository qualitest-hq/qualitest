/**
 * Staging 确认后的视口导航：解析「下一个应聚焦的待确认单元」。
 *
 * 聚焦顺序规则：
 * - 确认 addNode 后，若连线两端节点均已确认，优先聚焦其间可确认的连线
 * - 否则聚焦下一个 pending addNode
 * - 确认连线后，聚焦下一个 pending addNode
 */
import type { Edge } from '@vue-flow/core';

import type { AiStagingUnit } from '../types/aiStagingTypes';
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { isStagingConfirmBlockedByDependencies } from './stagingDependencyHints';
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

/** 单元当前是否满足依赖、可以确认 */
function isConfirmable(
  unitId: string,
  patch: FlowDesignPatch,
  confirmedUnitIds: ReadonlySet<string>,
): boolean {
  return !isStagingConfirmBlockedByDependencies(unitId, patch, confirmedUnitIds);
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

/** patch 中的 addNode 是否已确认；非 patch 新增节点视为已满足 */
function isPatchAddNodeConfirmed(
  nodeId: string,
  patch: FlowDesignPatch,
  confirmedUnitIds: ReadonlySet<string>,
): boolean {
  const inPatch = (patch.addNodes ?? []).some((n) => n.id === nodeId);
  if (!inPatch) return true;
  return confirmedUnitIds.has(`addNode:${nodeId}`);
}

/** 是否为连线类单元（聚焦时需包含 source/target 节点） */
export function isEdgeFocusUnit(unit: Pick<AiStagingUnit, 'kind'>): boolean {
  return isEdgeStagingKind(unit.kind);
}

/**
 * 刚确认一个单元后，解析同消息下下一个应聚焦的 pending 图单元。
 *
 * 返回 null 表示本消息内已无 pending 图单元，调用方可退而查找全局 pending。
 */
export function resolveNextStagingFocusUnit(
  confirmedUnit: AiStagingUnit,
  patch: FlowDesignPatch,
  unitsForMessage: AiStagingUnit[],
  confirmedUnitIds: ReadonlySet<string>,
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
    const readyEdges = graphPending.filter(
      (u) => isEdgeFocusUnit(u) && isConfirmable(u.unitId, patch, confirmedUnitIds),
    );
    // 查找与刚确认节点相连、且另一端节点也已确认的连线
    const bridgeEdge = readyEdges.find((u) => {
      const edgeId = objectIdFromUnitId(u.unitId);
      const endpoints = getEdgeEndpoints(edgeId, patch, edges);
      if (!endpoints) return false;
      const other =
        endpoints.source === confirmedNodeId
          ? endpoints.target
          : endpoints.target === confirmedNodeId
            ? endpoints.source
            : null;
      return other != null && isPatchAddNodeConfirmed(other, patch, confirmedUnitIds);
    });
    if (bridgeEdge) return bridgeEdge;

    const nextNode = graphPending.find((u) => u.kind === 'addNode');
    if (nextNode) return nextNode;

    if (readyEdges.length) return readyEdges[0];
  }

  if (confirmedUnit.kind === 'addEdge' || confirmedUnit.kind === 'updateEdge') {
    const nextNode = graphPending.find((u) => u.kind === 'addNode');
    if (nextNode) return nextNode;
  }

  const confirmable = graphPending.find((u) => isConfirmable(u.unitId, patch, confirmedUnitIds));
  return confirmable ?? graphPending[0] ?? null;
}
