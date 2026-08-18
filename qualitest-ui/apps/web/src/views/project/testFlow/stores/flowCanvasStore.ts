/**
 * 测试流画布页 Pinia 状态。
 *
 * 持有 vue-flow 的 nodes/edges/viewport、运行场景、flowOutputs、
 * 选中项、三栏 UI 开关，场景运行节点高亮，以及 AI 设计侧栏状态。
 */
import type { Edge, Node } from '@vue-flow/core';
import { computed, nextTick } from 'vue';

import type { GraphFlowOutput, GraphScenarioConfig, GraphViewport } from '@/utils/flow/graphTypes';

import { getTestProject } from '@/api/project/testProject';

import { createDefaultRunConfig, DEFAULT_VIEWPORT } from '../graphAdapter';
import { AI_CONFIRM_HIGHLIGHT_CLEAR_MS } from '../constants/flowConfig';
import { useAiStagingStore } from './aiStagingStore';

export { AI_CONFIRM_HIGHLIGHT_CLEAR_MS };

export type LeftPanelTab = 'runConfig' | 'nodes' | 'params' | 'runs';
export type RightPanelMode = 'props' | 'run' | 'scenario';

export interface FlowSelection {
  kind: 'node' | 'edge';
  id: string;
}

export interface FlowCanvasUi {
  leftCollapsed: boolean;
  rightOpen: boolean;
  leftTab: LeftPanelTab;
  rightMode: RightPanelMode;
  /** 小地图显隐（用户偏好，空画布时不渲染） */
  minimapVisible: boolean;
}

const MINIMAP_VISIBLE_STORAGE_KEY = 'qualitest.flowCanvas.minimapVisible';

let aiHighlightClearTimer: ReturnType<typeof setTimeout> | null = null;

function readMinimapVisiblePreference(): boolean {
  try {
    const raw = localStorage.getItem(MINIMAP_VISIBLE_STORAGE_KEY);
    if (raw === 'false') return false;
    if (raw === 'true') return true;
  } catch {
    // ignore
  }
  return true;
}

function persistMinimapVisible(visible: boolean) {
  try {
    localStorage.setItem(MINIMAP_VISIBLE_STORAGE_KEY, String(visible));
  } catch {
    // ignore
  }
}

export const useFlowCanvasStore = defineStore('flowCanvas', () => {
  const nodes = ref<Node[]>([]);
  const edges = ref<Edge[]>([]);
  const viewport = ref<GraphViewport>({ ...DEFAULT_VIEWPORT });
  const runConfig = ref<GraphScenarioConfig>(createDefaultRunConfig());
  const flowOutputs = ref<GraphFlowOutput[]>([]);
  const testFlowId = ref('');
  const testProjectId = ref('');
  const flowName = ref('');
  /** 项目 auth_config JSON 字符串，供画布解析双端凭证变量 */
  const projectAuthConfig = ref('');
  const dirty = ref(false);
  const loading = ref(false);
  const selected = ref<FlowSelection | null>(null);
  const ui = ref<FlowCanvasUi>({
    leftCollapsed: false,
    rightOpen: false,
    leftTab: 'runConfig',
    rightMode: 'props',
    minimapVisible: readMinimapVisiblePreference(),
  });
  /** 场景运行高亮：当前执行节点 id */
  const runHighlightNodeId = ref<string | null>(null);
  /** 场景运行已访问节点 id 集合 */
  const runVisitedNodeIds = ref<Record<string, 'passed' | 'failed'>>({});
  /**
   * 待灌入画布的边列表。
   * Staging 新增连线时先写入此处，等画布内节点注册完成后再合并进 edges。
   */
  const pendingEdges = ref<Edge[] | null>(null);
  /** 为 true 时暂缓 pushHistory，待节点初始化后建立撤销基线 */
  const pendingHistoryReset = ref(false);
  /**
   * 画布灌入/初始化期间为 true，屏蔽 VueFlow 节点/视口变更触发的 markDirty。
   * 仅负责抑制脏标记，不再承担视口恢复职责（视口恢复由 useFlowViewport 负责）。
   */
  const suppressDirty = ref(false);
  /** 上次加载或保存成功时的 graph_json 规范快照，用于判断能否回到「已保存」 */
  const savedGraphSnapshot = ref<string | null>(null);
  /** AI 设计侧栏/抽屉是否打开 */
  const aiDesignPanelOpen = ref(false);
  /** Run 详情「AI 修复」打开面板时待注入的 runId */
  const pendingAiDesignRunId = ref('');
  /** AI 确认成功后短暂高亮的新增/修改节点 id（紫色脉冲，约 4 秒后清除） */
  const aiHighlightNodeIds = ref<string[]>([]);
  /** Staging 边灌入请求计数；递增后触发画布内灌入逻辑重试 pendingEdges。 */
  const stagingEdgeFlushToken = ref(0);

  /** 递增灌入计数，通知画布内灌入逻辑重试 pendingEdges。 */
  function bumpStagingEdgeFlushToken() {
    stagingEdgeFlushToken.value += 1;
  }

  function clearAiHighlightTimer() {
    if (aiHighlightClearTimer != null) {
      clearTimeout(aiHighlightClearTimer);
      aiHighlightClearTimer = null;
    }
  }

  function scheduleAiHighlightClear() {
    clearAiHighlightTimer();
    aiHighlightClearTimer = setTimeout(() => {
      aiHighlightClearTimer = null;
      aiHighlightNodeIds.value = [];
    }, AI_CONFIRM_HIGHLIGHT_CLEAR_MS);
  }

  /** 确认成功后累积高亮节点；整批确认期间保持，需 finalizeAiConfirmHighlight 后统一清除 */
  function addAiConfirmHighlight(nodeIds: string[]) {
    if (!nodeIds.length) return;
    const merged = new Set([...aiHighlightNodeIds.value, ...nodeIds]);
    aiHighlightNodeIds.value = [...merged];
  }

  /** 本批 Staging 全部处理完后，延迟统一清除紫色高亮 */
  function finalizeAiConfirmHighlight() {
    if (!aiHighlightNodeIds.value.length) return;
    scheduleAiHighlightClear();
  }

  /** 定位 Staging 单元时临时高亮（替换当前高亮，仍参与统一清除计时） */
  function setAiHighlightFocus(nodeIds: string[]) {
    aiHighlightNodeIds.value = nodeIds.length ? [...nodeIds] : [];
    if (nodeIds.length) {
      scheduleAiHighlightClear();
    } else {
      clearAiHighlightTimer();
    }
  }

  /** 切换 testFlowId 或离开画布时清空编辑态 */
  function reset() {
    nodes.value = [];
    edges.value = [];
    viewport.value = { ...DEFAULT_VIEWPORT };
    runConfig.value = createDefaultRunConfig();
    flowOutputs.value = [];
    testFlowId.value = '';
    testProjectId.value = '';
    flowName.value = '';
    projectAuthConfig.value = '';
    dirty.value = false;
    loading.value = false;
    selected.value = null;
    ui.value = {
      leftCollapsed: false,
      rightOpen: false,
      leftTab: 'runConfig',
      rightMode: 'props',
      minimapVisible: readMinimapVisiblePreference(),
    };
    runHighlightNodeId.value = null;
    runVisitedNodeIds.value = {};
    pendingEdges.value = null;
    pendingHistoryReset.value = false;
    suppressDirty.value = false;
    savedGraphSnapshot.value = null;
    aiDesignPanelOpen.value = false;
    pendingAiDesignRunId.value = '';
    clearAiHighlightTimer();
    aiHighlightNodeIds.value = [];
    stagingEdgeFlushToken.value = 0;
    useAiStagingStore().reset();
  }

  /** 记录已保存基线（加载或保存成功后调用） */
  function setSavedGraphSnapshot(snapshot: string) {
    savedGraphSnapshot.value = snapshot;
  }

  /** 开始灌入图数据，暂停脏标记直到 endCanvasHydration */
  function beginCanvasHydration() {
    suppressDirty.value = true;
  }

  /** 画布与 VueFlow 初始化完成，恢复脏标记 */
  function endCanvasHydration() {
    suppressDirty.value = false;
  }

  /** 记录待灌入边；通常紧接在 nodes 更新之后、edges 正式写入之前调用。 */
  function setPendingEdges(next: Edge[]) {
    pendingEdges.value = next;
  }

  /**
   * 将 pendingEdges 合并进 store.edges。
   * 仅当每条边的 source/target 均存在于 nodes 时才写入；否则保留 pending 供后续重试。
   * 用于保存、运行、历史回退等不经过 Vue Flow 灌入组件的场景。
   */
  function flushPendingEdges() {
    const next = pendingEdges.value;
    if (!next?.length) return false;

    const nodeIds = new Set(nodes.value.map((n) => n.id));
    const endpointsReady = next.every((e) => nodeIds.has(e.source) && nodeIds.has(e.target));
    if (!endpointsReady) return false;

    edges.value = next.map((e) => ({ ...e }));
    const persisted = next.every((e) => edges.value.some((x) => x.id === e.id));
    if (persisted && edges.value.length >= next.length) {
      pendingEdges.value = null;
      return true;
    }
    return false;
  }

  /** 保存、场景运行或模拟前，尽力将 pending 边灌入 edges（最多重试一次）。 */
  async function ensureEdgesHydrated() {
    if (!pendingEdges.value?.length) return;
    flushPendingEdges();
    await nextTick();
    if (pendingEdges.value?.length && edges.value.length === 0) {
      flushPendingEdges();
      await nextTick();
    }
  }

  function markDirty() {
    if (suppressDirty.value) return;
    dirty.value = true;
  }

  function markClean() {
    dirty.value = false;
  }

  /** 选中节点或边，并切换到右栏属性模式（关闭 AI 助手） */
  function selectItem(kind: 'node' | 'edge', id: string) {
    aiDesignPanelOpen.value = false;
    selected.value = { kind, id };
    ui.value.rightMode = 'props';
    ui.value.rightOpen = true;
  }

  function clearSelection() {
    selected.value = null;
    if (ui.value.rightMode === 'props') {
      ui.value.rightOpen = false;
    }
  }

  function patchNodeData(nodeId: string, patch: Record<string, unknown>) {
    const node = nodes.value.find((n) => n.id === nodeId);
    if (!node) return;
    node.data = { ...node.data, ...patch };
    markDirty();
  }

  /** 更新边展示标签（仅画布展示，写入 graph_json.edges[].label） */
  function patchEdgeLabel(edgeId: string, label: string) {
    const idx = edges.value.findIndex((e) => e.id === edgeId);
    if (idx < 0) return;
    const trimmed = String(label ?? '');
    const next = { ...edges.value[idx] };
    if (trimmed) next.label = trimmed;
    else delete next.label;
    edges.value = edges.value.map((e, i) => (i === idx ? next : e));
    markDirty();
  }

  /** 打开右栏场景配置面板（关闭 AI 助手） */
  function showScenarioPanel() {
    aiDesignPanelOpen.value = false;
    ui.value.rightMode = 'scenario';
    ui.value.rightOpen = true;
  }

  /**
   * 切换左栏 Tab。
   * 离开运行配置时若右栏处于场景模式则回到属性模式；进入运行配置时自动打开场景面板。
   */
  function setLeftTab(tab: LeftPanelTab) {
    if (ui.value.leftTab === 'runConfig' && tab !== 'runConfig' && ui.value.rightMode === 'scenario') {
      ui.value.rightMode = 'props';
      if (!selected.value) {
        ui.value.rightOpen = false;
      }
    }
    ui.value.leftTab = tab;
    if (tab === 'runConfig') {
      showScenarioPanel();
    }
  }

  /** 场景运行开始后：右栏切运行详情，左栏切运行库 Tab（关闭 AI 助手） */
  function showRunPanel() {
    aiDesignPanelOpen.value = false;
    ui.value.rightMode = 'run';
    ui.value.rightOpen = true;
    ui.value.leftTab = 'runs';
  }

  function clearRunHighlight() {
    runHighlightNodeId.value = null;
    runVisitedNodeIds.value = {};
  }

  function toggleMinimapVisible() {
    ui.value.minimapVisible = !ui.value.minimapVisible;
    persistMinimapVisible(ui.value.minimapVisible);
  }

  function setMinimapVisible(visible: boolean) {
    ui.value.minimapVisible = visible;
    persistMinimapVisible(visible);
  }

  /** 打开 AI 助手侧栏，并关闭右栏（互斥） */
  function openAiDesignPanel() {
    aiDesignPanelOpen.value = true;
    ui.value.rightOpen = false;
  }

  /** 关闭 AI 助手侧栏 */
  function closeAiDesignPanel() {
    aiDesignPanelOpen.value = false;
  }

  /** 拉取项目 auth_config，供节点卡片解析凭证作用域 */
  async function loadProjectAuthConfig() {
    const id = testProjectId.value;
    if (!id) {
      projectAuthConfig.value = '';
      return;
    }
    try {
      const res = await getTestProject(id);
      projectAuthConfig.value = res.data?.authConfig || '';
    } catch {
      projectAuthConfig.value = '';
    }
  }

  /** 右栏是否实际展示：属性模式需有选中项 */
  const isRightPanelVisible = computed(() => {
    if (!ui.value.rightOpen) return false;
    if (ui.value.rightMode === 'props') return selected.value != null;
    return true;
  });

  return {
    nodes,
    edges,
    viewport,
    runConfig,
    flowOutputs,
    testFlowId,
    testProjectId,
    flowName,
    projectAuthConfig,
    dirty,
    loading,
    selected,
    ui,
    runHighlightNodeId,
    runVisitedNodeIds,
    pendingEdges,
    pendingHistoryReset,
    suppressDirty,
    savedGraphSnapshot,
    aiDesignPanelOpen,
    pendingAiDesignRunId,
    aiHighlightNodeIds,
    stagingEdgeFlushToken,
    setSavedGraphSnapshot,
    beginCanvasHydration,
    endCanvasHydration,
    bumpStagingEdgeFlushToken,
    setPendingEdges,
    flushPendingEdges,
    ensureEdgesHydrated,
    reset,
    markDirty,
    markClean,
    selectItem,
    clearSelection,
    patchNodeData,
    patchEdgeLabel,
    setLeftTab,
    showScenarioPanel,
    showRunPanel,
    clearRunHighlight,
    addAiConfirmHighlight,
    finalizeAiConfirmHighlight,
    setAiHighlightFocus,
    toggleMinimapVisible,
    setMinimapVisible,
    openAiDesignPanel,
    closeAiDesignPanel,
    loadProjectAuthConfig,
    isRightPanelVisible,
  };
});
