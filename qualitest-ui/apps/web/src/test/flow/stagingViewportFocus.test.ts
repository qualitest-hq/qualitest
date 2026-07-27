/**
 * 测 boundsFromNodeIds：单节点与多节点包围盒计算。
 * 边界：纯函数，无画布依赖。
 * 单跑：yarn test stagingViewportFocus   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { boundsFromNodeIds } from '@/views/project/testFlow/utils/stagingViewportFocus';

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
});
