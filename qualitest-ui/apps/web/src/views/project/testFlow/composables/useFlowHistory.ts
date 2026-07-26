/**
 * 画布撤销历史。
 *
 * 每次 pushHistory 深拷贝 nodes、edges、viewport、runConfig 以及 Staging 单元表入栈。
 * Ctrl+Z 回退时一并恢复 Staging 状态，避免撤销后单元状态与画布不一致。
 */
import type { Edge, Node } from '@vue-flow/core';
import { computed, nextTick, ref } from 'vue';
import { ElMessage } from 'element-plus';

import type { GraphScenarioConfig, GraphViewport } from '@/utils/flow/graphTypes';

import { normalizeViewport, rehydrateCanvasSnapshot } from '../graphAdapter';
import { HISTORY_MAX_STEPS } from '../constants/flowConfig';
import { reconcileFlowDirtyState } from '../utils/reconcileFlowDirty';
import { useAiStagingStore } from '../stores/aiStagingStore';
import type { AiStagingUnit } from '../types/aiStagingTypes';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';

/** 单次历史快照，字段均可 JSON 序列化 */
interface CanvasSnapshot {
  nodes: Array<{
    id: string;
    type: string;
    position: { x: number; y: number };
    data?: Record<string, unknown>;
  }>;
  edges: Array<{
    id: string;
    source: string;
    target: string;
    label?: string;
    type?: string;
    sourceHandle?: string | null;
  }>;
  viewport: GraphViewport;
  /** 运行场景；旧快照可能无此字段，回放时保留当前 runConfig */
  runConfig?: GraphScenarioConfig;
  /** 与画布同步的 Staging 单元快照；撤销时写回 store */
  stagingUnits?: Record<string, AiStagingUnit>;
}

const history = ref<CanvasSnapshot[]>([]);
const historyIndex = ref(-1);
/** 回放撤销结果期间为 true，避免 undo 写回再次入栈 */
const isRestoring = ref(false);

/** 剥离 vue-flow 运行时字段，只保留拓扑相关数据 */
function stripNodeForSnapshot(node: Node) {
  return {
    id: node.id,
    type: node.type,
    position: { x: node.position.x, y: node.position.y },
    data: JSON.parse(JSON.stringify(node.data ?? {})),
  };
}

function stripEdgeForSnapshot(edge: Edge) {
  const out: CanvasSnapshot['edges'][number] = {
    id: edge.id,
    source: edge.source,
    target: edge.target,
  };
  if (edge.label != null && String(edge.label).trim()) {
    out.label = String(edge.label).trim();
  }
  // condition 出边需保留 type 与 sourceHandle，撤销后才能挂回正确分支锚点
  if (edge.type === 'condition') {
    out.type = 'condition';
    out.sourceHandle = edge.sourceHandle ?? null;
  }
  return out;
}

/** 深拷贝当前 Staging 单元表，供历史快照使用 */
function captureStagingUnits(): Record<string, AiStagingUnit> {
  const stagingStore = useAiStagingStore();
  return JSON.parse(JSON.stringify(stagingStore.unitsById)) as Record<string, AiStagingUnit>;
}

/** 深拷贝当前画布拓扑、视口、运行场景与 Staging 单元 */
function captureSnapshot(store: ReturnType<typeof useFlowCanvasStore>): CanvasSnapshot {
  return {
    nodes: store.nodes.map(stripNodeForSnapshot),
    edges: store.edges.map(stripEdgeForSnapshot),
    viewport: normalizeViewport({ ...store.viewport }),
    runConfig: JSON.parse(JSON.stringify(store.runConfig)) as GraphScenarioConfig,
    stagingUnits: captureStagingUnits(),
  };
}

/**
 * 将历史快照写回 store。
 * 节点经 rehydrate 恢复 markRaw；边通过 pendingEdges 延迟灌入；runConfig 整体替换。
 */
async function applySnapshot(store: ReturnType<typeof useFlowCanvasStore>, snap: CanvasSnapshot) {
  const { nodes, edges } = rehydrateCanvasSnapshot(snap.nodes, snap.edges);
  store.setPendingEdges(edges);
  store.nodes = nodes;
  store.edges = [];
  store.viewport = normalizeViewport({ ...snap.viewport });
  if (snap.runConfig) {
    store.runConfig = JSON.parse(JSON.stringify(snap.runConfig)) as GraphScenarioConfig;
  }
  store.selected = null;
  await nextTick();
  store.flushPendingEdges();
  await nextTick();
  if (store.pendingEdges?.length && store.edges.length === 0) {
    store.flushPendingEdges();
  } else if (store.edges.length > 0) {
    store.pendingEdges = null;
  }
  await reconcileFlowDirtyState(store);

  // 恢复快照中的 Staging 单元状态（pending / confirmed / rejected 等）
  if (snap.stagingUnits) {
    const stagingStore = useAiStagingStore();
    stagingStore.unitsById = JSON.parse(JSON.stringify(snap.stagingUnits)) as Record<string, AiStagingUnit>;
  }
}

export function useFlowHistory() {
  const store = useFlowCanvasStore();

  const canUndo = computed(() => historyIndex.value > 0);

  /**
   * 将当前画布状态压入历史栈。
   * 连续相同快照不重复入栈；超出 HISTORY_MAX_STEPS 时丢弃最旧记录。
   */
  function pushHistory() {
    if (isRestoring.value) return;
    if (store.pendingHistoryReset) return;

    const snap = captureSnapshot(store);
    const serialized = JSON.stringify(snap);

    if (historyIndex.value >= 0) {
      const current = history.value[historyIndex.value];
      if (current && JSON.stringify(current) === serialized) return;
    }

    history.value = history.value.slice(0, historyIndex.value + 1);
    history.value.push(snap);
    historyIndex.value = history.value.length - 1;

    if (history.value.length > HISTORY_MAX_STEPS) {
      history.value.shift();
      historyIndex.value--;
    }
  }

  /** 回退一步历史，恢复节点、边、视口、runConfig 与 Staging 单元状态 */
  async function undo() {
    if (historyIndex.value <= 0) {
      ElMessage.info('没有可撤销的操作');
      return;
    }

    isRestoring.value = true;
    try {
      historyIndex.value--;
      const snap = history.value[historyIndex.value];
      if (snap) await applySnapshot(store, snap);
      ElMessage.success('已撤销');
    } finally {
      isRestoring.value = false;
    }
  }

  /** 清空历史并在当前画布状态上建立基线（节点初始化完成后调用） */
  function resetHistory() {
    history.value = [captureSnapshot(store)];
    historyIndex.value = 0;
  }

  /** 标记待建立历史基线，在 VueFlow onNodesInitialized 后执行 resetHistory */
  function scheduleHistoryReset() {
    store.pendingHistoryReset = true;
  }

  /** 节点就绪后建立基线；返回是否执行了 reset */
  function commitHistoryResetIfPending(): boolean {
    if (!store.pendingHistoryReset) return false;
    resetHistory();
    store.pendingHistoryReset = false;
    return true;
  }

  return {
    canUndo,
    pushHistory,
    undo,
    resetHistory,
    scheduleHistoryReset,
    commitHistoryResetIfPending,
    isRestoring,
  };
}
