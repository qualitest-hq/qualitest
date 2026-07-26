/**
 * Staging 画布节点/边同步计算。
 *
 * 根据 pending 状态的 Staging 单元，合并出应显示在画布上的 nodes 与 edges 列表。
 * 供画布同步逻辑与单测调用。
 */
import type { Edge, Node } from '@vue-flow/core';
import { markRaw } from 'vue';

import type { GraphEdge } from '@/utils/flow/graphTypes';

import type { AiStagingCanvasMark, AiStagingUnit } from '../types/aiStagingTypes';
import { applyConditionEdgeProps } from './conditionUtils';
import { mergeEdgeFields, mergeNodeFields } from './stagingFieldMerge';
import { graphObjectIdFromUnit } from './stagingUnitIds';
import { updateSummary } from './nodeDataUtils';
import { runStagingItemSync } from '../composables/useStagingSync';

/**
 * 将 pending 的 addNode 单元转为 vue-flow 节点。
 * draft 优先于 patchSlice；缺省 type 为 http，缺省 position 为 (40, 80)。
 * 非 addNode 或非 pending 返回 null。
 */
export function buildStagingNodeFromUnit(unit: AiStagingUnit): Node | null {
  if (unit.kind !== 'addNode' || unit.status !== 'pending') return null;
  const nodeId = graphObjectIdFromUnit(unit).nodeId;
  if (!nodeId) return null;

  const patchNode = unit.patchSlice as {
    type?: string;
    position?: { x: number; y: number };
    data?: Record<string, unknown>;
  } | undefined;
  const draft = unit.draft ?? {};

  const nodeType = (draft.type as string) ?? patchNode?.type ?? 'http';
  const data = {
    ...((patchNode?.data ?? {}) as Record<string, unknown>),
    ...((draft.data as Record<string, unknown> | undefined) ?? {}),
  };
  if (nodeType === 'http') {
    updateSummary('http', data);
  }

  return markRaw({
    id: nodeId,
    type: nodeType,
    position: (draft.position as { x: number; y: number }) ??
      patchNode?.position ?? { x: 40, y: 80 },
    data,
  }) as Node;
}

/**
 * 将 pending 的 addEdge 单元转为 vue-flow 边。
 * source/target 取自 draft 或 patchSlice；缺任一端点返回 null。
 * 若源节点为条件节点，会补全条件边样式属性。
 */
export function buildStagingEdgeFromUnit(unit: AiStagingUnit, nodes: Node[]): Edge | null {
  if (unit.kind !== 'addEdge' || unit.status !== 'pending') return null;
  const edgeId = graphObjectIdFromUnit(unit).edgeId;
  if (!edgeId) return null;

  const patchEdge = unit.patchSlice as GraphEdge | undefined;
  const draft = unit.draft ?? {};
  const source = String((draft.source as string) ?? patchEdge?.source ?? '').trim();
  const target = String((draft.target as string) ?? patchEdge?.target ?? '').trim();
  if (!source || !target) return null;

  const edge: Edge = {
    id: edgeId,
    source,
    target,
  };
  if (draft.label != null && String(draft.label).trim()) {
    edge.label = String(draft.label);
  } else if (patchEdge?.label) {
    edge.label = patchEdge.label;
  }

  const srcNode = nodes.find((n) => n.id === source);
  applyConditionEdgeProps(edge, srcNode, patchEdge);
  return edge;
}

/** 将 pending 的 updateNode 单元 draft 合并进现有节点列表；无变化返回 null。 */
function applyNodeDraft(unit: AiStagingUnit, nodes: Node[]): Node[] | null {
  if (unit.kind !== 'updateNode' || unit.status !== 'pending') return null;
  const nodeId = graphObjectIdFromUnit(unit).nodeId;
  if (!nodeId) return null;

  const index = nodes.findIndex((n) => n.id === nodeId);
  if (index < 0) return null;

  const existing = nodes[index];
  const draft = unit.draft ?? {};
  const next = mergeNodeFields(existing, {
    type: draft.type as string | undefined,
    position: draft.position as { x: number; y: number } | undefined,
    data: draft.data as Record<string, unknown> | undefined,
  });

  if (JSON.stringify(next) === JSON.stringify(existing)) return null;
  const cloned = [...nodes];
  cloned[index] = next;
  return cloned;
}

/** 将 pending 的 updateEdge 单元 draft 合并进现有边列表；无变化返回 null。 */
function applyEdgeDraft(unit: AiStagingUnit, edges: Edge[]): Edge[] | null {
  if (unit.kind !== 'updateEdge' || unit.status !== 'pending') return null;
  const edgeId = graphObjectIdFromUnit(unit).edgeId;
  if (!edgeId) return null;

  const index = edges.findIndex((e) => e.id === edgeId);
  if (index < 0) return null;

  const existing = edges[index];
  const draft = unit.draft ?? {};
  const next = mergeEdgeFields(existing, {
    source: draft.source as string | undefined,
    target: draft.target as string | undefined,
    label: draft.label as string | undefined,
  });

  if (JSON.stringify(next) === JSON.stringify(existing)) return null;
  const cloned = [...edges];
  cloned[index] = next;
  return cloned;
}

/** 画布同步所需的上下文：Staging 单元、当前画布节点/边、高亮标记、节点裁剪回调。 */
export interface StagingCanvasSyncContext {
  /** 全部 Staging 单元，按 unitId 索引 */
  unitsById: Record<string, AiStagingUnit>;
  /** 画布当前节点列表 */
  canvasNodes: Node[];
  /** 画布当前边列表 */
  canvasEdges: Edge[];
  /** 节点 id → Staging 高亮信息（add/update/delete） */
  stagingByNodeId: Record<string, AiStagingCanvasMark>;
  /** 边 id → Staging 高亮信息 */
  stagingByEdgeId: Record<string, AiStagingCanvasMark>;
  /** addNode 单元被移除时，顺带清理以该节点为端点的边 */
  onPrunedNodeAdd?: (nodeId: string) => void;
}

/**
 * 计算 Staging 同步后画布应展示的 nodes 与 edges。
 *
 * 先同步节点层（addNode + updateNode），再基于最新节点列表同步边层（addEdge + updateEdge）。
 * 边层依赖节点层结果，以便条件边属性与端点校验正确。
 */
export function computeStagingCanvasSync(ctx: StagingCanvasSyncContext): {
  nodes: Node[];
  edges: Edge[];
} {
  const nodes = runStagingItemSync(ctx.unitsById, ctx.canvasNodes, {
    isLayerKind: (unit) => unit.kind === 'addNode' || unit.kind === 'updateNode',
    buildFromAddUnit: buildStagingNodeFromUnit,
    applyUpdateUnit: applyNodeDraft,
    getItemId: (node) => node.id,
    getMark: (id) => ctx.stagingByNodeId[id],
    onPrunedAdd: (nodeId) => ctx.onPrunedNodeAdd?.(nodeId),
  });

  const edges = runStagingItemSync(ctx.unitsById, ctx.canvasEdges, {
    isLayerKind: (unit) => unit.kind === 'addEdge' || unit.kind === 'updateEdge',
    buildFromAddUnit: (unit) => buildStagingEdgeFromUnit(unit, nodes),
    applyUpdateUnit: applyEdgeDraft,
    getItemId: (edge) => edge.id,
    getMark: (id) => ctx.stagingByEdgeId[id],
  });

  return { nodes, edges };
}

/** 列出所有 pending 状态的新增连线单元。 */
export function listPendingStagingEdgeUnits(unitsById: Record<string, AiStagingUnit>): AiStagingUnit[] {
  return Object.values(unitsById).filter((u) => u.kind === 'addEdge' && u.status === 'pending');
}

/** 组装画布同步上下文对象。 */
export function createStagingCanvasSyncContext(
  unitsById: Record<string, AiStagingUnit>,
  canvasNodes: Node[],
  canvasEdges: Edge[],
  stagingByNodeId: StagingCanvasSyncContext['stagingByNodeId'],
  stagingByEdgeId: StagingCanvasSyncContext['stagingByEdgeId'],
  onPrunedNodeAdd?: (nodeId: string) => void,
): StagingCanvasSyncContext {
  return {
    unitsById,
    canvasNodes,
    canvasEdges,
    stagingByNodeId,
    stagingByEdgeId,
    onPrunedNodeAdd,
  };
}
