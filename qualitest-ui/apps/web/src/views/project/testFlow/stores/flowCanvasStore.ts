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

import { partitionTemplateParams, mergeEnvPreviewRows, templateEnvsToEnvParamRows } from '../../testProjectTemplate/utils/templateParamUtils';
import { createDefaultRunConfig, DEFAULT_VIEWPORT } from '../graphAdapter';
import { AI_CONFIRM_HIGHLIGHT_CLEAR_MS } from '../constants/flowConfig';
import { waitDoubleAnimationFrame } from '../utils/waitDoubleAnimationFrame';
import { useAiStagingStore } from './aiStagingStore';

export { AI_CONFIRM_HIGHLIGHT_CLEAR_MS };

export type LeftPanelTab = 'runConfig' | 'nodes' | 'params' | 'runs';
export type RightPanelMode = 'props' | 'run' | 'scenario';
/** project=真实项目测试流；template=项目模板预制流（无真实 projectId） */
export type FlowCanvasMode = 'project' | 'template';

export interface TemplateApiCatalogEntry {
  syntheticId: string;
  api: Record<string, unknown>;
}

/** 模板画布参数库上下文（来自 templateParams + templateEnvs） */
export interface TemplateParamContext {
  env: Array<{ name: string; value?: unknown; remark?: string }>;
  asset: Array<{ name: string; value?: unknown; remark?: string }>;
}

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
  /** 画布上下文：project=测试项目流；template=项目模板预制流（只读浏览） */
  const canvasMode = ref<FlowCanvasMode>('project');
  /** 模板模式下由 templateApis 合成的接口目录（供 Http 配置展示绑定） */
  const templateApiCatalog = ref<TemplateApiCatalogEntry[]>([]);
  const templateApiTree = ref<unknown[]>([]);
  /** 模板模式下 templateParams + templateEnvs 水合（env/asset 预览，不请求项目变量接口） */
  const templateParamContext = ref<TemplateParamContext | null>(null);
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
  /**
   * 外部同步（MCP 等）差分高亮节点 id，与 runHighlight / aiHighlight 分字段。
   * 新增为主高亮，变更亦列入此集合。
   */
  const externalSyncHighlightNodeIds = ref<string[]>([]);
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

  /** 外部落盘同步后的差分高亮 */
  function setExternalSyncHighlight(nodeIds: string[]) {
    externalSyncHighlightNodeIds.value = nodeIds.length ? [...nodeIds] : [];
  }

  /** 清除外部同步高亮 */
  function clearExternalSyncHighlight() {
    externalSyncHighlightNodeIds.value = [];
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
    canvasMode.value = 'project';
    templateApiCatalog.value = [];
    templateApiTree.value = [];
    templateParamContext.value = null;
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
    externalSyncHighlightNodeIds.value = [];
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

  /** 当前可用于序列化/校验的边（store.edges 优先，否则 pendingEdges）。 */
  function getEffectiveEdges(): Edge[] {
    if (edges.value.length) return edges.value;
    return pendingEdges.value ?? [];
  }

  /**
   * 请求 AiStagingEdgeFlush 将 pendingEdges 灌入 Vue Flow（须节点与 handle 已就绪）。
   * 不再直接写 store.edges，避免 Vue Flow 静默丢弃边并清空 pending。
   */
  function requestPendingEdgeFlush() {
    if (pendingEdges.value?.length) {
      bumpStagingEdgeFlushToken();
    }
  }

  /** 保存、运行前：触发灌边并等待 Vue Flow 写入 store.edges。 */
  async function ensureEdgesHydrated() {
    if (edges.value.length || !pendingEdges.value?.length) return;

    for (let attempt = 0; attempt < 12; attempt++) {
      bumpStagingEdgeFlushToken();
      await nextTick();
      await waitDoubleAnimationFrame();
      if (edges.value.length > 0) return;
    }

    // 兜底：至少保证落盘/撤销栈有边数据（画布可能仍待 refresh）
    // 灌入期间禁止强行写 edges，避免 condition handle 未就绪时连线错位
    if (
      !suppressDirty.value &&
      pendingEdges.value?.length &&
      edges.value.length === 0
    ) {
      edges.value = pendingEdges.value.map((e) => ({ ...e }));
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
    if (canvasMode.value === 'template') {
      projectAuthConfig.value = '';
      return;
    }
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

  /**
   * 切入模板预制流画布：标记 canvasMode=template（只读权限据此生效），
   * 并挂上合成后的接口树/目录供 HTTP 节点展示绑定。
   */
  function setTemplateApiContext(tree: unknown[], catalog: TemplateApiCatalogEntry[]) {
    canvasMode.value = 'template';
    templateApiTree.value = tree;
    templateApiCatalog.value = catalog;
  }

  /** 写入模板预制参数上下文（asset + templateEnvs 预览） */
  function setTemplateParamContext(params: unknown[], envs: unknown[] = []) {
    const partitioned = partitionTemplateParams(params);
    templateParamContext.value = {
      env: mergeEnvPreviewRows(templateEnvsToEnvParamRows(envs)),
      asset: partitioned.asset,
    };
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
    canvasMode,
    templateApiCatalog,
    templateApiTree,
    templateParamContext,
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
    externalSyncHighlightNodeIds,
    stagingEdgeFlushToken,
    setSavedGraphSnapshot,
    beginCanvasHydration,
    endCanvasHydration,
    bumpStagingEdgeFlushToken,
    setPendingEdges,
    getEffectiveEdges,
    requestPendingEdgeFlush,
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
    setExternalSyncHighlight,
    clearExternalSyncHighlight,
    toggleMinimapVisible,
    setMinimapVisible,
    openAiDesignPanel,
    closeAiDesignPanel,
    loadProjectAuthConfig,
    setTemplateApiContext,
    setTemplateParamContext,
    isRightPanelVisible,
  };
});
