/**
 * 测 spreadStagingAddPositions：Staging 灌入前 addNodes AABB 避让。
 * 边界：纯函数，无画布依赖。
 * 单跑：yarn test spreadStagingAddPositions   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import type { FlowDesignPatch } from '@/views/project/testFlow/types/aiDesignTypes';
import {
  obstaclesFromCanvasNodes,
  spreadStagingAddPositions,
  STAGING_GRID_X,
} from '@/views/project/testFlow/utils/spreadStagingAddPositions';

describe('spreadStagingAddPositions', () => {
  it('同位两节点 → 第二颗 x+GRID_X', () => {
    // 前提：同批两个 addNodes 都写 (40,80)，无既有障碍
    // 期望：第一颗保留，第二颗右移一格
    const patch: FlowDesignPatch = {
      addNodes: [
        { id: '1', type: 'http', position: { x: 40, y: 80 }, data: {} },
        { id: '2', type: 'http', position: { x: 40, y: 80 }, data: {} },
      ],
    };

    const out = spreadStagingAddPositions(patch, []);

    expect(out.addNodes?.[0].position).toEqual({ x: 40, y: 80 });
    expect(out.addNodes?.[1].position).toEqual({ x: 40 + STAGING_GRID_X, y: 80 });
  });

  it('已有障碍在 40,80 → 新节点避开', () => {
    // 前提：画布已有节点在 (40,80)，新 addNode 也写 (40,80)
    // 期望：新节点错开到 (420,80)
    const patch: FlowDesignPatch = {
      addNodes: [
        { id: 'n', type: 'http', position: { x: 40, y: 80 }, data: {} },
      ],
    };

    const out = spreadStagingAddPositions(patch, [{ x: 40, y: 80 }]);

    expect(out.addNodes?.[0].position).toEqual({ x: 40 + STAGING_GRID_X, y: 80 });
  });

  it('obstaclesFromCanvasNodes 可排除本轮 id', () => {
    // 前提：画布含本轮将重写的节点 id
    // 期望：excludeIds 中的节点不计入障碍
    const obstacles = obstaclesFromCanvasNodes(
      [
        { id: 'keep', position: { x: 40, y: 80 } },
        { id: 'rewrite', position: { x: 100, y: 100 } },
      ],
      new Set(['rewrite']),
    );
    expect(obstacles).toEqual([{ x: 40, y: 80 }]);
  });
});
