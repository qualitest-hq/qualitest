/**
 * Staging 节点/边在 vue-flow 画布上的注入、draft 同步与移除。
 *
 * 监听 Staging 单元变化，将 pending 变更合并到画布 store；
 * 新增连线先写入 pendingEdges，待节点在画布内注册完成后再灌入 edges。
 */
import type { Edge, Node } from '@vue-flow/core';

import { mergeEdgeFields, mergeNodeFields } from '../utils/stagingFieldMerge';
import type { AiStagingUnit } from '../types/aiStagingTypes';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import {
  computeStagingCanvasSync,
  createStagingCanvasSyncContext,
  listPendingStagingEdgeUnits,
} from '../utils/stagingCanvasCompute';
import { useStagingSync } from './useStagingSync';

/** 从画布节点提取可写入 Staging draft 的字段（type、position、data）。 */
export function buildDraftFromCanvasNode(node: Node): Record<string, unknown> {
  return {
    type: node.type,
    position: { x: node.position.x, y: node.position.y },
    data: JSON.parse(JSON.stringify(node.data ?? {})),
  };
}

/** 从画布边提取可写入 Staging draft 的字段（source、target、label）。 */
export function buildDraftFromCanvasEdge(edge: Edge): Record<string, unknown> {
  const draft: Record<string, unknown> = {
    source: edge.source,
    target: edge.target,
  };
  if (edge.label != null && String(edge.label).trim()) {
    draft.label = String(edge.label);
  }
  return draft;
}

/** 从画布移除指定 Staging 节点，并删除以其为端点的所有边。 */
export function removeStagingNodeFromCanvas(nodeId: string) {
  const store = useFlowCanvasStore();
  store.nodes = store.nodes.filter((n) => n.id !== nodeId);
  store.edges = store.edges.filter((e) => e.source !== nodeId && e.target !== nodeId);
  if (store.pendingEdges?.length) {
    store.setPendingEdges(
      store.pendingEdges.filter((e) => e.source !== nodeId && e.target !== nodeId),
    );
  }
}

/** 从画布移除指定 Staging 边。 */
export function removeStagingEdgeFromCanvas(edgeId: string) {
  const store = useFlowCanvasStore();
  store.edges = store.edges.filter((e) => e.id !== edgeId);
  if (store.pendingEdges?.length) {
    store.setPendingEdges(store.pendingEdges.filter((e) => e.id !== edgeId));
  }
}

/** 用确认前保存的 baseline 恢复节点字段（拒绝或回滚 updateNode 时调用）。 */
export function restoreNodeFromBaseline(nodeId: string, baseline?: Record<string, unknown>) {
  if (!baseline) return;
  const store = useFlowCanvasStore();
  const index = store.nodes.findIndex((n) => n.id === nodeId);
  if (index < 0) return;

  const existing = store.nodes[index];
  const next = mergeNodeFields(existing, {
    type: baseline.type as string | undefined,
    position: baseline.position as { x: number; y: number } | undefined,
    data: baseline.data as Record<string, unknown> | undefined,
  });
  const nodes = [...store.nodes];
  nodes[index] = next;
  store.nodes = nodes;
}

/** 用确认前保存的 baseline 恢复边字段（拒绝或回滚 updateEdge 时调用）。 */
export function restoreEdgeFromBaseline(edgeId: string, baseline?: Record<string, unknown>) {
  if (!baseline) return;
  const store = useFlowCanvasStore();
  const index = store.edges.findIndex((e) => e.id === edgeId);
  if (index < 0) return;

  const existing = store.edges[index];
  const next = mergeEdgeFields(existing, {
    source: baseline.source as string | undefined,
    target: baseline.target as string | undefined,
    label: baseline.label as string | undefined,
  });
  const edges = [...store.edges];
  edges[index] = next;
  store.edges = edges;
}

/**
 * 将 Staging 同步结果写入画布 store。
 *
 * 节点有变化时更新 store.nodes。
 * 边有变化、或仍有 pending 新增连线时：写入 pendingEdges 并递增灌入计数；
 * 完整边表以 getEffectiveEdges（含 pending）为比较基准，避免确认落盘窗口 edges 被清空后误判丢边。
 */
export function applyStagingCanvasToStore(
  store: ReturnType<typeof useFlowCanvasStore>,
  nodes: Node[],
  edges: Edge[],
  unitsById: Record<string, AiStagingUnit>,
) {
  if (JSON.stringify(nodes) !== JSON.stringify(store.nodes)) {
    store.nodes = nodes;
  }

  const hasPendingAddEdges = listPendingStagingEdgeUnits(unitsById).length > 0;
  const edgesChanged = JSON.stringify(edges) !== JSON.stringify(store.getEffectiveEdges());
  if (edgesChanged || hasPendingAddEdges) {
    store.setPendingEdges(edges);
    store.bumpStagingEdgeFlushToken();
  }
}

/**
 * Staging 画布同步 composable。
 *
 * 订阅 Staging 单元变化，在确认请求进行中时跳过同步，避免与后端合并结果冲突。
 */
export function useAiStagingCanvas() {
  const store = useFlowCanvasStore();
  const stagingStore = useAiStagingStore();

  /** 根据当前 Staging 单元重算并写入画布 nodes / pendingEdges。 */
  function syncStagingCanvas() {
    if (Object.values(stagingStore.unitsById).some((unit) => unit.confirmInFlight)) {
      return;
    }

    // 确认落盘窗口 store.edges 常被清空、完整边在 pendingEdges；必须用有效边表作底，否则会覆盖丢边
    const baseEdges = store.getEffectiveEdges().map((e) => ({ ...e }));

    const ctx = createStagingCanvasSyncContext(
      stagingStore.unitsById,
      store.nodes,
      baseEdges,
      stagingStore.stagingByNodeId,
      stagingStore.stagingByEdgeId,
      (nodeId) => {
        store.edges = store.edges.filter((e) => e.source !== nodeId && e.target !== nodeId);
        if (store.pendingEdges?.length) {
          store.setPendingEdges(
            store.pendingEdges.filter((e) => e.source !== nodeId && e.target !== nodeId),
          );
        }
      },
    );
    const { nodes, edges } = computeStagingCanvasSync(ctx);
    applyStagingCanvasToStore(store, nodes, edges, stagingStore.unitsById);
  }

  useStagingSync(syncStagingCanvas, () => stagingStore.unitsById);

  return {
    syncStagingCanvas,
    removeStagingNodeFromCanvas,
    removeStagingEdgeFromCanvas,
    buildDraftFromCanvasNode,
    buildDraftFromCanvasEdge,
    restoreNodeFromBaseline,
    restoreEdgeFromBaseline,
  };
}
