/**
 * Staging 单元 confirm 成功后的画布节点高亮 id 收集。
 */
import type { Edge } from '@vue-flow/core';

import type { DiffItemKind, FlowDesignPatch } from '../types/aiDesignTypes';
import { objectIdFromUnitId } from './stagingUnitIds';

export function collectStagingConfirmHighlightIds(
  unitId: string,
  kind: DiffItemKind | string,
  edges: Edge[],
  patch?: FlowDesignPatch,
): string[] {
  if (kind === 'addNode' || kind === 'updateNode' || kind === 'deleteNode') {
    return [objectIdFromUnitId(unitId)];
  }
  if (kind === 'addEdge' || kind === 'updateEdge' || kind === 'deleteEdge') {
    const edgeId = objectIdFromUnitId(unitId);
    const edge = edges.find((e) => e.id === edgeId);
    if (edge) return [edge.source, edge.target];
    const fromPatch =
      patch?.addEdges?.find((e) => e.id === edgeId) ??
      patch?.updateEdges?.find((e) => e.id === edgeId);
    if (fromPatch?.source && fromPatch?.target) {
      return [fromPatch.source, fromPatch.target];
    }
    return [];
  }
  return [];
}
