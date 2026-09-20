/**
 * 测整图分层坐标计算：链式分层、分支同层错开、无边换行、长链折行、按节点宽高留白。
 * 边界：纯函数，无画布依赖。
 * 单跑：pnpm test flowGraphLayeredLayout   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import {
  computeLayeredPositions,
  LAYOUT_GAP_X,
  LAYOUT_GAP_Y,
  LAYOUT_LAYER_GAP_X,
  LAYOUT_NODE_GAP_Y,
  LAYOUT_ORIGIN_X,
  LAYOUT_ORIGIN_Y,
} from '@/views/project/testFlow/utils/flowGraphLayeredLayout'

describe('computeLayeredPositions', () => {
  it('链式边左到右分层', () => {
    // 前提：A→B→C，标准宽高
    // 期望：x 按标准层间距递增，y 同为原点
    const pos = computeLayeredPositions(
      [{ id: 'A' }, { id: 'B' }, { id: 'C' }],
      [
        { source: 'A', target: 'B' },
        { source: 'B', target: 'C' },
      ],
    )
    expect(pos.get('A')).toEqual({ x: LAYOUT_ORIGIN_X, y: LAYOUT_ORIGIN_Y })
    expect(pos.get('B')).toEqual({
      x: LAYOUT_ORIGIN_X + LAYOUT_LAYER_GAP_X,
      y: LAYOUT_ORIGIN_Y,
    })
    expect(pos.get('C')).toEqual({
      x: LAYOUT_ORIGIN_X + 2 * LAYOUT_LAYER_GAP_X,
      y: LAYOUT_ORIGIN_Y,
    })
  })

  it('分支同层上下错开', () => {
    // 前提：R→A、R→B
    // 期望：A/B 同 x，y 相差标准行距
    const pos = computeLayeredPositions(
      [{ id: 'R' }, { id: 'A' }, { id: 'B' }],
      [
        { source: 'R', target: 'A' },
        { source: 'R', target: 'B' },
      ],
    )
    expect(pos.get('A')?.x).toBe(pos.get('B')?.x)
    expect(Math.abs((pos.get('A')?.y ?? 0) - (pos.get('B')?.y ?? 0))).toBe(LAYOUT_NODE_GAP_Y)
  })

  it('无边时按列换行', () => {
    // 前提：5 个孤立标准节点
    // 期望：第 5 个落到第二行
    const nodes = [0, 1, 2, 3, 4].map((i) => ({ id: `N${i}` }))
    const pos = computeLayeredPositions(nodes, [])
    expect(pos.get('N3')?.y).toBe(LAYOUT_ORIGIN_Y)
    expect(pos.get('N4')).toEqual({
      x: LAYOUT_ORIGIN_X,
      y: LAYOUT_ORIGIN_Y + LAYOUT_NODE_GAP_Y,
    })
  })

  it('长链按列折行兼顾宽高', () => {
    // 前提：A→…→F 六节点链
    // 期望：D 折到下一行带左侧，非整图一长条
    const ids = ['A', 'B', 'C', 'D', 'E', 'F']
    const nodes = ids.map((id) => ({ id }))
    const edges = ids.slice(0, -1).map((id, i) => ({
      source: id,
      target: ids[i + 1],
    }))
    const pos = computeLayeredPositions(nodes, edges)
    expect(pos.get('D')?.x).toBe(LAYOUT_ORIGIN_X)
    expect((pos.get('D')?.y ?? 0) > (pos.get('A')?.y ?? 0)).toBe(true)
  })

  it('宽高不同时按实际尺寸留白', () => {
    // 前提：宽节点 W(400×108) → 高节点 T(300×200) 与矮节点 S 同层
    // 期望：T.x = W.x + W.w + GAP_X；S.y = T 高度下移
    const pos = computeLayeredPositions(
      [
        { id: 'W', width: 400, height: 108 },
        { id: 'T', width: 300, height: 200 },
        { id: 'S', width: 300, height: 108 },
      ],
      [
        { source: 'W', target: 'T' },
        { source: 'W', target: 'S' },
      ],
    )
    expect(pos.get('W')).toEqual({ x: LAYOUT_ORIGIN_X, y: LAYOUT_ORIGIN_Y })
    expect(pos.get('T')?.x).toBe(LAYOUT_ORIGIN_X + 400 + LAYOUT_GAP_X)
    expect(pos.get('S')?.x).toBe(pos.get('T')?.x)
    expect(pos.get('S')?.y).toBe(LAYOUT_ORIGIN_Y + 200 + LAYOUT_GAP_Y)
  })
})
