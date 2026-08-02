/**
 * Staging 视口聚焦辅助：从节点 id 列表计算 flow 坐标系下的包围盒。
 *
 * 供 focusNodeIds 定位目标区域；节点未找到时返回 null。
 */
import type { Node } from '@vue-flow/core';

import { NODE_MIN_H, NODE_W } from '../constants/flowConfig';

/** flow 坐标系下的矩形包围盒 */
export interface FlowBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * 根据节点 id 列表，在画布节点集合中计算并集包围盒。
 * 每个节点按标准节点宽高（NODE_W × NODE_MIN_H）估算边界；
 * 多节点时取最小外接矩形，宽高不低于单节点尺寸。
 */
export function boundsFromNodeIds(nodeIds: string[], nodes: Node[]): FlowBounds | null {
  const idSet = new Set(nodeIds);
  const xs: number[] = [];
  const ys: number[] = [];
  for (const node of nodes) {
    if (!idSet.has(node.id)) continue;
    xs.push(node.position.x, node.position.x + NODE_W);
    ys.push(node.position.y, node.position.y + NODE_MIN_H);
  }
  if (!xs.length) return null;
  return {
    x: Math.min(...xs),
    y: Math.min(...ys),
    width: Math.max(Math.max(...xs) - Math.min(...xs), NODE_W),
    height: Math.max(Math.max(...ys) - Math.min(...ys), NODE_MIN_H),
  };
}

/**
 * 判断包围盒是否与当前视口可见区相交（含边距）。
 * 用于 Staging 确认后「已能看见则不再平移」，避免整图看起来像节点在跑。
 */
export function isBoundsVisibleInViewport(
  bounds: FlowBounds,
  viewport: { x: number; y: number; zoom: number },
  paneWidth: number,
  paneHeight: number,
  options?: { marginPx?: number; dockOverlapPx?: number },
): boolean {
  const zoom = viewport.zoom > 0 ? viewport.zoom : 1;
  const margin = (options?.marginPx ?? 24) / zoom;
  const dock = options?.dockOverlapPx ?? 0;
  const left = -viewport.x / zoom + margin;
  const top = -viewport.y / zoom + margin;
  const right = left + Math.max(0, paneWidth - dock) / zoom - 2 * margin;
  const bottom = top + paneHeight / zoom - 2 * margin;
  if (right <= left || bottom <= top) return false;
  return !(
    bounds.x + bounds.width < left ||
    bounds.x > right ||
    bounds.y + bounds.height < top ||
    bounds.y > bottom
  );
}
