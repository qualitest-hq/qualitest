/**
 * 小地图：样式 props、显隐、节点点击导航。
 */
import type { GraphNode } from '@vue-flow/core';
import { useVueFlow } from '@vue-flow/core';
import { computed } from 'vue';

import { FLOW_VUE_FLOW_ID } from '../constants/flowConfig';
import {
  STAGING_ADD_COLOR,
  STAGING_ADD_FILL,
  STAGING_ADD_STROKE,
  STAGING_DELETE_FILL,
  STAGING_DELETE_STROKE,
  STAGING_UPDATE_FILL,
  STAGING_UPDATE_STROKE,
} from '../constants/stagingTheme';
import { NODE_TYPES, type FlowNodeTypeKey } from '../constants/nodeTypes';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { pathSimulateActive } from './useFlowSimulate';

export const MINIMAP_WIDTH = 168;
export const MINIMAP_HEIGHT = 112;
/** 圆角 = min(宽, 高) × 比例，与节点绝对尺寸无关 */
export const MINIMAP_NODE_RADIUS_RATIO = 0.1;
export const MINIMAP_NODE_RADIUS_MAX = 12;
export const MINIMAP_MASK_BORDER_RADIUS = 4;
export const MINIMAP_MASK_COLOR = 'rgba(51, 65, 85, 0.16)';
export const MINIMAP_MASK_STROKE_COLOR = 'rgba(11, 110, 220, 0.75)';

const RUNNING_FILL = '#16a34a';
const PASSED_FILL = '#86efac';
const FAILED_FILL = '#fca5a5';
const FAILED_STROKE = '#dc2626';
const RUN_STROKE = '#15803d';
const RUN_PASSED_STROKE = '#16a34a';
const AI_HIGHLIGHT_FILL = STAGING_ADD_COLOR;
const AI_STAGING_ADD_FILL = STAGING_ADD_FILL;
const AI_STAGING_UPDATE_FILL = STAGING_UPDATE_FILL;
const AI_STAGING_DELETE_FILL = STAGING_DELETE_FILL;
const DEFAULT_FILL = '#94a3b8';

/** 小地图默认：节点类型主色的浅色底 */
const MINIMAP_BASE_MIX = 0.45;
/** 路径模拟：当前步完整主色，已访问略浅 */
const SIMULATE_PASSED_MIX = 0.68;
const SIMULATE_PASSED_STROKE_MIX = 0.78;

type MinimapNodeEvent = {
  event: MouseEvent;
  node: GraphNode;
};

type SimulateMinimapTone = { fill: string; stroke: string };

function nodeTypeAccent(node: GraphNode): string {
  const type = String(node.type ?? '');
  return NODE_TYPES[type as FlowNodeTypeKey]?.color ?? DEFAULT_FILL;
}

/** 将 hex 与白色混合，weight 越大越接近原色 */
export function mixHexWithWhite(hex: string, weight: number): string {
  const normalized = hex.trim().replace('#', '');
  if (!/^[0-9a-fA-F]{6}$/.test(normalized)) return hex;
  const r = parseInt(normalized.slice(0, 2), 16);
  const g = parseInt(normalized.slice(2, 4), 16);
  const b = parseInt(normalized.slice(4, 6), 16);
  const w = Math.min(1, Math.max(0, weight));
  const mix = (channel: number) => Math.round(255 * (1 - w) + channel * w);
  return `rgb(${mix(r)}, ${mix(g)}, ${mix(b)})`;
}

/** 小地图节点圆角：按短边比例，保证各节点视觉弧度一致 */
export function resolveMinimapNodeRadius(width: number, height: number): number {
  const w = Math.max(0, width);
  const h = Math.max(0, height);
  if (!w || !h) return 0;
  const minSide = Math.min(w, h);
  return Math.min(
    minSide * MINIMAP_NODE_RADIUS_RATIO,
    w / 2,
    h / 2,
    MINIMAP_NODE_RADIUS_MAX,
  );
}

function defaultMinimapFill(node: GraphNode): string {
  return mixHexWithWhite(nodeTypeAccent(node), MINIMAP_BASE_MIX);
}

/** 路径模拟期间：按节点类型主色返回填充/描边，非高亮节点返回 null */
function resolveSimulateMinimapTone(
  node: GraphNode,
  id: string,
  store: ReturnType<typeof useFlowCanvasStore>,
): SimulateMinimapTone | null {
  if (!pathSimulateActive.value) return null;

  const accent = nodeTypeAccent(node);
  if (store.runHighlightNodeId === id) {
    return { fill: accent, stroke: accent };
  }

  const visited = store.runVisitedNodeIds[id];
  if (visited === 'failed') {
    return { fill: FAILED_FILL, stroke: FAILED_STROKE };
  }
  if (visited === 'passed') {
    return {
      fill: mixHexWithWhite(accent, SIMULATE_PASSED_MIX),
      stroke: mixHexWithWhite(accent, SIMULATE_PASSED_STROKE_MIX),
    };
  }
  return null;
}

function resolveMinimapNodeColor(node: GraphNode, store: ReturnType<typeof useFlowCanvasStore>): string {
  const id = node.id;
  const stagingStore = useAiStagingStore();
  const stagingMark = stagingStore.stagingByNodeId[id];

  const simulateTone = resolveSimulateMinimapTone(node, id, store);
  if (simulateTone) return simulateTone.fill;

  if (store.runHighlightNodeId === id) return RUNNING_FILL;

  const visited = store.runVisitedNodeIds[id];
  if (visited === 'failed') return FAILED_FILL;
  if (visited === 'passed') return PASSED_FILL;

  if (store.aiHighlightNodeIds.includes(id)) return AI_HIGHLIGHT_FILL;

  if (stagingMark?.mode === 'delete') return AI_STAGING_DELETE_FILL;
  if (stagingMark?.mode === 'update') return AI_STAGING_UPDATE_FILL;
  if (stagingMark?.mode === 'add') return AI_STAGING_ADD_FILL;

  return defaultMinimapFill(node);
}

function resolveMinimapNodeStrokeColor(node: GraphNode, store: ReturnType<typeof useFlowCanvasStore>): string {
  const id = node.id;
  const stagingStore = useAiStagingStore();
  const stagingMark = stagingStore.stagingByNodeId[id];

  if (node.selected || (store.selected?.kind === 'node' && store.selected.id === id)) {
    return MINIMAP_MASK_STROKE_COLOR;
  }

  const simulateTone = resolveSimulateMinimapTone(node, id, store);
  if (simulateTone) return simulateTone.stroke;

  if (store.runHighlightNodeId === id) return RUN_STROKE;

  const visited = store.runVisitedNodeIds[id];
  if (visited === 'failed') return FAILED_STROKE;
  if (visited === 'passed') return RUN_PASSED_STROKE;

  if (store.aiHighlightNodeIds.includes(id)) return STAGING_ADD_STROKE;

  if (stagingMark?.mode === 'delete') return STAGING_DELETE_STROKE;
  if (stagingMark?.mode === 'update') return STAGING_UPDATE_STROKE;
  if (stagingMark?.mode === 'add') return STAGING_ADD_STROKE;

  return 'transparent';
}

function trackMinimapColorDeps(store: ReturnType<typeof useFlowCanvasStore>) {
  void pathSimulateActive.value;
  void store.runHighlightNodeId;
  void store.runVisitedNodeIds;
  void store.aiHighlightNodeIds;
  const stagingStore = useAiStagingStore();
  void stagingStore.stagingByNodeId;
}

export function useFlowMinimap() {
  const store = useFlowCanvasStore();
  const { setCenter, fitView, addSelectedNodes, removeSelectedElements } = useVueFlow(FLOW_VUE_FLOW_ID);

  const minimapNodeColor = computed(() => {
    trackMinimapColorDeps(store);
    return (node: GraphNode) => resolveMinimapNodeColor(node, store);
  });

  const minimapNodeStrokeColor = computed(() => {
    trackMinimapColorDeps(store);
    void store.selected;
    return (node: GraphNode) => resolveMinimapNodeStrokeColor(node, store);
  });

  const showMinimap = computed(() => store.ui.minimapVisible);

  function nodeCenter(node: GraphNode) {
    const w = node.dimensions?.width ?? 0;
    const h = node.dimensions?.height ?? 0;
    return {
      x: node.position.x + w / 2,
      y: node.position.y + h / 2,
    };
  }

  function focusNode(node: GraphNode) {
    removeSelectedElements();
    addSelectedNodes([node.id]);
    store.selectItem('node', node.id);
  }

  function onMinimapNodeClick({ node }: MinimapNodeEvent) {
    focusNode(node);
    const center = nodeCenter(node);
    setCenter(center.x, center.y, { zoom: store.viewport.zoom, duration: 280 });
  }

  function onMinimapNodeDblClick({ node }: MinimapNodeEvent) {
    focusNode(node);
    fitView({ nodes: [node.id], padding: 0.28, duration: 320 });
  }

  return {
    showMinimap,
    minimapNodeColor,
    minimapNodeStrokeColor,
    minimapMaskColor: MINIMAP_MASK_COLOR,
    minimapMaskStrokeColor: MINIMAP_MASK_STROKE_COLOR,
    minimapMaskBorderRadius: MINIMAP_MASK_BORDER_RADIUS,
    minimapWidth: MINIMAP_WIDTH,
    minimapHeight: MINIMAP_HEIGHT,
    onMinimapNodeClick,
    onMinimapNodeDblClick,
  };
}
