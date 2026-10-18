/**
 * 测整图分层坐标计算：链式分层、分支同层、菱形汇合对齐、无边换行、长链折行、按节点宽高留白。
 * 边界：纯函数，无画布依赖。
 * 单跑：pnpm test flowGraphLayeredLayout   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import { NODE_MIN_H } from '@/views/project/testFlow/constants/flowConfig'
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

  it('长链按列折行', () => {
    // 前提：A→…→F 六节点链
    // 期望：按约 √N 列折行，D 落到下一行带左侧，不拉成一行
    const ids = ['A', 'B', 'C', 'D', 'E', 'F']
    const nodes = ids.map((id) => ({ id }))
    const edges = ids.slice(0, -1).map((id, i) => ({
      source: id,
      target: ids[i + 1],
    }))
    const pos = computeLayeredPositions(nodes, edges)
    expect(pos.get('D')?.x).toBe(LAYOUT_ORIGIN_X)
    expect((pos.get('D')?.y ?? 0) > (pos.get('A')?.y ?? 0)).toBe(true)
    expect(pos.get('C')?.y).toBe(pos.get('A')?.y)
  })

  it('菱形汇合落在两路中间', () => {
    // 前提：R→A、R→B，A→M、B→M；节点登记顺序把 B 放在 A 前，但出边先 A 后 B
    // 期望：A 在 B 上方且同层；M 的垂直中心靠近 A、B 中心的中点
    const pos = computeLayeredPositions(
      [{ id: 'R' }, { id: 'B' }, { id: 'A' }, { id: 'M' }],
      [
        { source: 'R', target: 'A' },
        { source: 'R', target: 'B' },
        { source: 'A', target: 'M' },
        { source: 'B', target: 'M' },
      ],
    )
    expect(pos.get('A')?.x).toBe(pos.get('B')?.x)
    expect((pos.get('A')?.y ?? 0) < (pos.get('B')?.y ?? 0)).toBe(true)
    const h = NODE_MIN_H
    const aMid = (pos.get('A')?.y ?? 0) + h / 2
    const bMid = (pos.get('B')?.y ?? 0) + h / 2
    const mMid = (pos.get('M')?.y ?? 0) + h / 2
    expect(Math.abs(mMid - (aMid + bMid) / 2)).toBeLessThan(1)
    expect((pos.get('M')?.x ?? 0) > (pos.get('A')?.x ?? 0)).toBe(true)
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
    expect(pos.get('T')?.x).toBe(LAYOUT_ORIGIN_X + 400 + LAYOUT_GAP_X)
    expect(pos.get('S')?.x).toBe(pos.get('T')?.x)
    expect(pos.get('S')?.y).toBe((pos.get('T')?.y ?? 0) + 200 + LAYOUT_GAP_Y)
    // 分支列整体中心靠近父节点垂直中心
    const wCenter = (pos.get('W')?.y ?? 0) + NODE_MIN_H / 2
    const stackTop = pos.get('T')?.y ?? 0
    const stackCenter = stackTop + (200 + LAYOUT_GAP_Y + NODE_MIN_H) / 2
    expect(Math.abs(stackCenter - wCenter)).toBeLessThan(1)
  })
})
