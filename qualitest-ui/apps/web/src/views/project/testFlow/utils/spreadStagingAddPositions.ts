/**
 * Staging 灌入前的新增节点坐标处理。
 * <p>
 * 忽略模型可能自带的坐标；按节点类型估算宽高，用网格起步 + AABB 避让，
 * 给本批 addNodes 写入互不重叠的 position，供预览展示（不写库）。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import {
  findFreeLayoutPosition,
  LAYOUT_LAYER_GAP_X,
  resolveLayoutSize,
  wrapPreferredPosition,
  type LayoutRect,
} from './flowGraphLayeredLayout';

/** 画布上已占用的节点矩形，作为避让障碍 */
export type StagingPositionObstacle = LayoutRect;

/**
 * 为 patch 中的 addNodes 重新写入 position。
 * 不修改入参原对象；没有 addNodes 时原样返回。
 * 每放置一颗节点后立刻加入障碍列表，避免同批互相重叠。
 */
export function spreadStagingAddPositions(
  patch: FlowDesignPatch,
  obstacles: StagingPositionObstacle[],
): FlowDesignPatch {
  const addNodes = patch.addNodes;
  if (!addNodes?.length) {
    return patch;
  }

  const placed: LayoutRect[] = [...obstacles];
  const nextNodes = addNodes.map((node) => {
    const branches = (node.data as Record<string, unknown> | undefined)?.branches;
    const size = resolveLayoutSize({
      id: node.id ?? '',
      type: node.type,
      branchCount: Array.isArray(branches) ? branches.length : undefined,
    });
    const preferred = wrapPreferredPosition(placed.length);
    const free = findFreeLayoutPosition(preferred, placed, size);
    placed.push({ ...free, w: size.w, h: size.h });
    return {
      ...node,
      position: { x: free.x, y: free.y },
    };
  });

  return { ...patch, addNodes: nextNodes };
}

/**
 * 从当前画布节点列表提取避让障碍矩形（含按类型估算的宽高）。
 * excludeIds 中的节点不计入（例如本轮即将重写的同 id 节点）。
 */
export function obstaclesFromCanvasNodes(
  nodes: Array<{
    id?: string
    type?: string | null
    position?: { x?: number; y?: number } | null
    data?: Record<string, unknown> | null
  }>,
  excludeIds?: ReadonlySet<string>,
): StagingPositionObstacle[] {
  const out: StagingPositionObstacle[] = [];
  for (const n of nodes) {
    if (!n || (n.id && excludeIds?.has(n.id))) {
      continue;
    }
    const x = n.position?.x;
    const y = n.position?.y;
    if (typeof x !== 'number' || typeof y !== 'number' || Number.isNaN(x) || Number.isNaN(y)) {
      continue;
    }
    const branches = n.data?.branches;
    const size = resolveLayoutSize({
      id: n.id ?? '',
      type: n.type,
      branchCount: Array.isArray(branches) ? branches.length : undefined,
    });
    out.push({ x, y, w: size.w, h: size.h });
  }
  return out;
}
