/**
 * Staging 灌入前：对 addNodes 做与画布障碍物的 AABB 避让，避免多轮 pending 叠在同一坐标。
 */
import type { FlowDesignPatch } from '../types/aiDesignTypes';
import { NODE_MIN_H, NODE_W } from '../constants/flowConfig';

/** 与服务端 FlowDesignPatchNormalizer 网格步进一致 */
export const STAGING_GRID_X = 380;

const DEFAULT_X = 40;
const DEFAULT_Y = 80;
/** 单次右移 / 下移行尝试上限；用尽后兜底落点，避免死循环 */
const MAX_SHIFT = 40;
const ROW_STEP = NODE_MIN_H + 40;

export interface StagingPositionObstacle {
  x: number;
  y: number;
}

function boxesOverlap(
  ax: number,
  ay: number,
  bx: number,
  by: number,
): boolean {
  return !(
    ax + NODE_W <= bx
    || bx + NODE_W <= ax
    || ay + NODE_MIN_H <= by
    || by + NODE_MIN_H <= ay
  );
}

function overlapsAny(
  x: number,
  y: number,
  obstacles: StagingPositionObstacle[],
): boolean {
  for (const o of obstacles) {
    if (boxesOverlap(x, y, o.x, o.y)) {
      return true;
    }
  }
  return false;
}

/**
 * 在障碍物中为候选点找空位：优先沿 x 网格右移，用尽后 y 下移再继续。
 */
function findFreeStagingPosition(
  preferred: StagingPositionObstacle,
  obstacles: StagingPositionObstacle[],
): StagingPositionObstacle {
  const baseX = preferred.x;
  const baseY = preferred.y;
  for (let row = 0; row < MAX_SHIFT; row++) {
    const y = baseY + row * ROW_STEP;
    for (let i = 0; i < MAX_SHIFT; i++) {
      const x = baseX + i * STAGING_GRID_X;
      if (!overlapsAny(x, y, obstacles)) {
        return { x, y };
      }
    }
  }
  // 与服务端一致：兜底落在扫过范围的右下角外侧
  return {
    x: baseX + MAX_SHIFT * STAGING_GRID_X,
    y: baseY + MAX_SHIFT * ROW_STEP,
  };
}

/**
 * 重写 patch.addNodes 的 position，使其不与障碍物及同批已放置节点重叠。
 * 不修改原 patch；无 addNodes 时原样返回。
 */
export function spreadStagingAddPositions(
  patch: FlowDesignPatch,
  obstacles: StagingPositionObstacle[],
): FlowDesignPatch {
  const addNodes = patch.addNodes;
  if (!addNodes?.length) {
    return patch;
  }

  const placed: StagingPositionObstacle[] = [...obstacles];
  const nextNodes = addNodes.map((node) => {
    const preferred = {
      x: node.position?.x ?? DEFAULT_X,
      y: node.position?.y ?? DEFAULT_Y,
    };
    const free = findFreeStagingPosition(preferred, placed);
    placed.push(free);
    if (free.x === preferred.x && free.y === preferred.y && node.position) {
      return node;
    }
    return {
      ...node,
      position: { x: free.x, y: free.y },
    };
  });

  return { ...patch, addNodes: nextNodes };
}

/**
 * 从画布节点提取避让障碍；可排除本轮 patch 即将写入的 id（同 message 重灌时避免把旧副本当障碍）。
 */
export function obstaclesFromCanvasNodes(
  nodes: Array<{ id?: string; position?: { x?: number; y?: number } | null }>,
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
    out.push({ x, y });
  }
  return out;
}
