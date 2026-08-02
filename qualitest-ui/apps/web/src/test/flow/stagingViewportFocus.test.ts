/**
 * 测 boundsFromNodeIds：单节点与多节点包围盒计算。
 * 边界：纯函数，无画布依赖。
 * 单跑：yarn test stagingViewportFocus   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  boundsFromNodeIds,
  isBoundsVisibleInViewport,
} from '@/views/project/testFlow/utils/stagingViewportFocus';

describe('stagingViewportFocus', () => {
  it('boundsFromNodeIds 计算包围盒', () => {
    // 前提：单个节点位置已知
    // 期望：返回含默认宽高的包围盒
    const bounds = boundsFromNodeIds(
      ['a'],
      [{ id: 'a', position: { x: 100, y: 200 } } as never],
    );
    expect(bounds).toEqual({
      x: 100,
      y: 200,
      width: 300,
      height: 108,
    });
  });

  it('boundsFromNodeIds 多节点取并集', () => {
    // 前提：两个节点位置分散
    // 期望：包围盒覆盖两者并集
    const bounds = boundsFromNodeIds(
      ['a', 'b'],
      [
        { id: 'a', position: { x: 0, y: 0 } },
        { id: 'b', position: { x: 400, y: 100 } },
      ] as never[],
    );
    expect(bounds).toEqual({
      x: 0,
      y: 0,
      width: 700,
      height: 208,
    });
  });

  it('isBoundsVisibleInViewport：与视野相交为 true，完全偏出为 false', () => {
    // 前提：视口覆盖 flow (0,0)-(800,600)，目标部分在视野内 / 远在右侧
    // 期望：相交可见，完全偏出不可见
    const viewport = { x: 0, y: 0, zoom: 1 };
    const visible = isBoundsVisibleInViewport(
      { x: 100, y: 100, width: 300, height: 108 },
      viewport,
      800,
      600,
      { marginPx: 0 },
    );
    const partial = isBoundsVisibleInViewport(
      { x: 700, y: 100, width: 300, height: 108 },
      viewport,
      800,
      600,
      { marginPx: 0 },
    );
    const offscreen = isBoundsVisibleInViewport(
      { x: 2000, y: 100, width: 300, height: 108 },
      viewport,
      800,
      600,
      { marginPx: 0 },
    );
    expect(visible).toBe(true);
    expect(partial).toBe(true);
    expect(offscreen).toBe(false);
  });
});
