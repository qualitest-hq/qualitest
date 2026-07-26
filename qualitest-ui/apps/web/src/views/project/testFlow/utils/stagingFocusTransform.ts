/**
 * Staging 视口聚焦的纯数学变换。
 *
 * 根据节点包围盒、画布尺寸、AI 侧栏遮挡宽度和目标缩放，
 * 一次算出 viewport 的平移与缩放，使目标落在用户可见区域中央。
 */
import type { GraphViewport } from '@/utils/flow/graphTypes';

import { AI_DOCK_WIDTH_MAX_PX, AI_DOCK_WIDTH_VW } from '../constants/flowConfig';
import type { FlowBounds } from './stagingViewportFocus';

/** computeStagingFocusViewport 的输入参数 */
export interface StagingFocusTransformInput {
  /** 目标节点在 flow 坐标系下的包围盒 */
  bounds: FlowBounds;
  /** 画布可视区域宽度（像素） */
  paneWidth: number;
  /** 画布可视区域高度（像素） */
  paneHeight: number;
  /** AI 侧栏从右侧遮挡的宽度（像素），侧栏关闭时为 0 */
  dockOverlapPx: number;
  /** 应用后的缩放倍率 */
  zoom: number;
}

/**
 * 根据画布宽度计算 AI 侧栏遮挡区域的水平像素宽度。
 * 宽度无效时返回 0；有效时取「固定上限」与「视口宽度 × 比例」的较小值。
 */
export function resolveAiDockOverlapPx(paneWidth: number): number {
  if (paneWidth <= 0) return 0;
  return Math.min(AI_DOCK_WIDTH_MAX_PX, paneWidth * AI_DOCK_WIDTH_VW);
}

/**
 * 决定 Staging 聚焦时使用的缩放倍率。
 * 只缩小不放大：当前倍率已低于上限时保持现状，避免连续确认时反复缩放跳动。
 */
export function resolveStagingFocusZoom(currentZoom: number, cap: number): number {
  if (currentZoom <= 0) return cap;
  return Math.min(currentZoom, cap);
}

/**
 * 计算使包围盒中心对齐到可见区域中心的 viewport。
 *
 * 可见区域水平中心 = (画布宽 - 侧栏遮挡) / 2，垂直中心 = 画布高 / 2。
 * 平移量 x、y 使 flow 坐标下的包围盒中心映射到上述屏幕中心位置。
 */
export function computeStagingFocusViewport(input: StagingFocusTransformInput): GraphViewport {
  const { bounds, paneWidth, paneHeight, dockOverlapPx, zoom } = input;
  const cx = bounds.x + bounds.width / 2;
  const cy = bounds.y + bounds.height / 2;
  const visibleCenterX = (paneWidth - dockOverlapPx) / 2;
  const visibleCenterY = paneHeight / 2;
  return {
    x: visibleCenterX - cx * zoom,
    y: visibleCenterY - cy * zoom,
    zoom,
  };
}
