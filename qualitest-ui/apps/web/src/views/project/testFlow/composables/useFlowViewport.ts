/**
 * 画布视口唯一控制器。
 *
 * 所有程序化视口变更（Staging 聚焦、底栏缩放、加载恢复等）统一经本模块写入，
 * 写入顺序固定为：先更新 store.viewport，再调用 Vue Flow 的 setViewport。
 * 用户拖拽/滚轮操作则反向同步：Vue Flow → store。
 *
 * 通过 programmaticDepth 计数屏蔽程序化变更期间 Vue Flow 触发的 onViewportChange，
 * 避免动画未完成时旧值回写导致视角跳变。
 */
import { useVueFlow } from '@vue-flow/core';
import { nextTick } from 'vue';

import type { GraphViewport } from '@/utils/flow/graphTypes';

import {
  FLOW_VUE_FLOW_ID,
  STAGING_FOCUS_ZOOM,
} from '../constants/flowConfig';
import { normalizeViewport } from '../graphAdapter';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import {
  computeStagingFocusViewport,
  resolveAiDockOverlapPx,
  resolveStagingFocusZoom,
} from '../utils/stagingFocusTransform';
import { boundsFromNodeIds } from '../utils/stagingViewportFocus';

/** Staging 聚焦与 fitView 的默认动画时长（毫秒） */
const STAGING_ANIM_MS = 320;
/** 底栏缩放允许的最小倍率 */
const MIN_ZOOM = 0.25;
/** 底栏缩放允许的最大倍率 */
const MAX_ZOOM = 2;
/** 底栏每次放大/缩小的倍率步进 */
const ZOOM_FACTOR = 1.15;

/**
 * 当前进行中的程序化视口写入层数。
 * applyViewport / restoreFromStore / fitViewAll 进入时 +1，结束时 -1；
 * 大于 0 时 syncFromFlow 忽略 Vue Flow 上报的视口变化。
 */
let programmaticDepth = 0;

/** 将缩放倍率限制在底栏允许范围内 */
function clampZoom(zoom: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom));
}

export function useFlowViewport() {
  const store = useFlowCanvasStore();
  const {
    setViewport,
    getViewport,
    fitView: vfFitView,
    dimensions,
  } = useVueFlow(FLOW_VUE_FLOW_ID);

  /**
   * 计算 AI 设计侧栏当前遮挡画布的水平像素宽度。
   * 侧栏关闭时返回 0；打开时按画布宽度与配置上限取较小值。
   */
  function resolveDockOverlap(): number {
    if (!store.aiDesignPanelOpen) return 0;
    return resolveAiDockOverlapPx(dimensions.value.width);
  }

  /**
   * 程序化设置视口：先写 store，再驱动 Vue Flow。
   * @param vp 目标视口 { x, y, zoom }
   * @param options.animate 为 false 时无动画（duration=0）
   * @param options.duration 动画毫秒数，默认 STAGING_ANIM_MS
   */
  async function applyViewport(
    vp: GraphViewport,
    options?: { animate?: boolean; duration?: number },
  ) {
    const normalized = normalizeViewport(vp);
    programmaticDepth += 1;
    store.viewport = normalized;
    try {
      const duration = options?.animate === false ? 0 : (options?.duration ?? STAGING_ANIM_MS);
      await setViewport(normalized, { duration });
    } finally {
      programmaticDepth -= 1;
    }
  }

  /**
   * 用户通过拖拽或滚轮改变视口时，将 Vue Flow 当前值同步到 store。
   * 灌入/聚焦等程序化变更期间不处理，防止中间态覆盖目标值。
   * <p>
   * 不标记 dirty：平移/缩放是浏览操作，不进入撤销栈；
   * 下次「真正改图」并保存时仍会把当前视口一并写入 graph_json。
   */
  function syncFromFlow(vp: GraphViewport) {
    if (programmaticDepth > 0) return;
    store.viewport = normalizeViewport(vp);
  }

  /**
   * 将 store 中保存的视口立即应用到 Vue Flow，不播放动画。
   * 用于画布挂载、节点初始化完成、加载灌图后恢复用户视角。
   */
  async function restoreFromStore() {
    const v = store.viewport;
    programmaticDepth += 1;
    try {
      await setViewport({ x: v.x, y: v.y, zoom: v.zoom }, { duration: 0 });
    } finally {
      programmaticDepth -= 1;
    }
  }

  /**
   * 等待画布布局稳定后再执行视口聚焦。
   * 依次：等待 DOM 更新 → 双帧 rAF → 灌入待处理边 → 触发边灌入重试 → 再次等待布局。
   * 供 Staging 确认落盘后、节点尺寸与连线就绪再移动视角。
   */
  async function waitForCanvasReady() {
    await nextTick();
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
    });
    store.flushPendingEdges();
    store.bumpStagingEdgeFlushToken();
    await nextTick();
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
    });
  }

  /**
   * 等待视口动画结束，并从 Vue Flow 读取最终值写回 store。
   * 默认等待 STAGING_ANIM_MS + 一帧余量，供 pushHistory 记录正确视口。
   */
  async function waitForViewportSettled(ms = STAGING_ANIM_MS) {
    await new Promise<void>((resolve) => setTimeout(resolve, ms + 16));
    store.viewport = normalizeViewport(getViewport());
  }

  /**
   * 将视口平移到指定节点（或连线两端节点）所在区域。
   * 在扣除 AI 侧栏后的可见区内居中；缩放只缩小不放大，上限默认 90%。
   * @returns 是否成功计算出目标并应用视口
   */
  async function focusNodeIds(nodeIds: string[], options?: { zoomCap?: number }) {
    if (!nodeIds.length) return false;

    const bounds = boundsFromNodeIds(nodeIds, store.nodes);
    const paneW = dimensions.value.width;
    const paneH = dimensions.value.height;
    if (!bounds || paneW <= 0 || paneH <= 0) return false;

    const cap = options?.zoomCap ?? STAGING_FOCUS_ZOOM;
    const zoom = resolveStagingFocusZoom(store.viewport.zoom, cap);
    const vp = computeStagingFocusViewport({
      bounds,
      paneWidth: paneW,
      paneHeight: paneH,
      dockOverlapPx: resolveDockOverlap(),
      zoom,
    });
    await applyViewport(vp);
    return true;
  }

  /**
   * 底栏「适应画布」：缩放并平移以展示全部节点，完成后同步视口到 store。
   * @param padding 四周留白比例，默认 0.2
   */
  async function fitViewAll(padding = 0.2) {
    programmaticDepth += 1;
    try {
      await vfFitView({ padding, duration: STAGING_ANIM_MS });
      store.viewport = normalizeViewport(getViewport());
    } finally {
      programmaticDepth -= 1;
    }
  }

  /** 底栏放大：在当前平移位置下提高缩放倍率 */
  async function zoomIn() {
    const v = store.viewport;
    await applyViewport({ x: v.x, y: v.y, zoom: clampZoom(v.zoom * ZOOM_FACTOR) }, { duration: 0 });
  }

  /** 底栏缩小：在当前平移位置下降低缩放倍率 */
  async function zoomOut() {
    const v = store.viewport;
    await applyViewport({ x: v.x, y: v.y, zoom: clampZoom(v.zoom / ZOOM_FACTOR) }, { duration: 0 });
  }

  /** 底栏重置缩放：保持当前平移，倍率恢复为 100% */
  async function resetZoom() {
    const v = store.viewport;
    await applyViewport({ x: v.x, y: v.y, zoom: 1 }, { duration: 0 });
  }

  return {
    applyViewport,
    syncFromFlow,
    restoreFromStore,
    waitForCanvasReady,
    waitForViewportSettled,
    focusNodeIds,
    fitViewAll,
    zoomIn,
    zoomOut,
    resetZoom,
    clampZoom,
    /** 当前缩放倍率对应的百分比整数，供底栏显示 */
    zoomPercent: () => Math.round(store.viewport.zoom * 100),
  };
}
