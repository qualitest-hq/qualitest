/**
 * 测 ELK 分层排版：链式左右、链式上下、菱形汇合到右侧、折行、引擎失败回退。
 * 边界：纯函数，不挂画布。回退用会抛错的假引擎。
 * 手测：稍复杂流分别用三种预设；横纵切换后句柄与折线方向正确；撤销可恢复排版前坐标。
 * 单跑：pnpm test flowElkLayout   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import { layoutWithElk } from '@/views/project/testFlow/utils/flowElkLayout'
import { LAYOUT_ORIGIN_X, LAYOUT_ORIGIN_Y } from '@/views/project/testFlow/utils/flowGraphLayeredLayout'
import { presetById } from '@/views/project/testFlow/utils/flowLayoutPreset'

function longHorizontalY(points: Array<{ x: number; y: number }> | undefined): number | null {
  if (!points) return null
  let best: { y: number; span: number } | null = null
  for (let i = 1; i < points.length; i++) {
    const span = Math.abs(points[i].x - points[i - 1].x)
    if (Math.abs(points[i].y - points[i - 1].y) > 1 || span < 80) continue
    if (!best || span > best.span) best = { y: points[i].y, span }
  }
  return best?.y ?? null
}

const chain = [
  { id: 'A', width: 200, height: 100 },
  { id: 'B', width: 200, height: 100 },
]
const chainEdges = [{ source: 'A', target: 'B' }]

describe('layoutWithElk', () => {
  it('横向分层把下一层放到右侧', async () => {
    // 前提：A→B，方向向右，默认间距
    // 期望：B 在 A 右侧，纵坐标接近，左上对齐到原点附近
    const { positions, fallback } = await layoutWithElk(chain, chainEdges, presetById('horizontal'))
    const a = positions.get('A')!
    const b = positions.get('B')!
    expect(fallback).toBe(false)
    expect(a.x).toBe(LAYOUT_ORIGIN_X)
    expect(a.y).toBe(LAYOUT_ORIGIN_Y)
    expect(b.x).toBeGreaterThan(a.x + 100)
    expect(Math.abs(b.y - a.y)).toBeLessThan(20)
  })

  it('纵向分层把下一层放到下方', async () => {
    // 前提：A→B，方向向下
    // 期望：B 在 A 下方，横坐标接近
    const { positions, fallback } = await layoutWithElk(chain, chainEdges, presetById('vertical'))
    const a = positions.get('A')!
    const b = positions.get('B')!
    expect(fallback).toBe(false)
    expect(b.y).toBeGreaterThan(a.y + 100)
    expect(Math.abs(b.x - a.x)).toBeLessThan(20)
  })

  it('菱形汇合点排在两个分支之后', async () => {
    // 前提：R 分到 A、B，再汇到 M
    // 期望：A、B 同层且上下分开，M 在更右侧
    const { positions } = await layoutWithElk(
      [
        { id: 'R', width: 200, height: 100 },
        { id: 'A', width: 200, height: 100 },
        { id: 'B', width: 200, height: 100 },
        { id: 'M', width: 200, height: 100 },
      ],
      [
        { source: 'R', target: 'A' },
        { source: 'R', target: 'B' },
        { source: 'A', target: 'M' },
        { source: 'B', target: 'M' },
      ],
      presetById('horizontal'),
    )
    const a = positions.get('A')!
    const b = positions.get('B')!
    const m = positions.get('M')!
    expect(Math.abs(a.x - b.x)).toBeLessThan(20)
    expect(Math.abs(a.y - b.y)).toBeGreaterThan(40)
    expect(m.x).toBeGreaterThan(a.x)
    expect(m.x).toBeGreaterThan(b.x)
  })

  it('两条跨层边的水平段不落在同一高度', async () => {
    // 前提：两个条件的 ELSE 都连到同一个终点，各自占用一个入边端口
    // 期望：两条 ELSE 的长水平段纵坐标分开
    const nodes = [
      { id: 'C1', width: 220, height: 140, sourcePortOrder: ['out-if', 'out-else'] },
      { id: 'H', width: 200, height: 100 },
      { id: 'C2', width: 220, height: 140, sourcePortOrder: ['out-if', 'out-else'] },
      { id: 'Login', width: 200, height: 100 },
      { id: 'A', width: 200, height: 120 },
    ]
    const edges = [
      { id: 'if1', source: 'C1', target: 'H', sourceHandle: 'out-if' },
      { id: 'else1', source: 'C1', target: 'A', sourceHandle: 'out-else' },
      { id: 'h', source: 'H', target: 'C2' },
      { id: 'if2', source: 'C2', target: 'Login', sourceHandle: 'out-if' },
      { id: 'else2', source: 'C2', target: 'A', sourceHandle: 'out-else' },
      { id: 'login', source: 'Login', target: 'A' },
    ]
    const { routes, fallback } = await layoutWithElk(nodes, edges, presetById('horizontal'))
    expect(fallback).toBe(false)
    const y1 = longHorizontalY(routes.get('else1')!)
    const y2 = longHorizontalY(routes.get('else2')!)
    expect(y1).not.toBeNull()
    expect(y2).not.toBeNull()
    expect(Math.abs(y1! - y2!)).toBeGreaterThan(16)
  })

  it('横向折行把长链拆成多排', async () => {
    // 前提：八个同尺寸节点串成一条链，预设为横向折行
    // 期望：不是全部排在同一高度，后面的节点落到下一排
    const ids = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']
    const { positions, fallback } = await layoutWithElk(
      ids.map((id) => ({ id, width: 300, height: 120 })),
      ids.slice(0, -1).map((id, index) => ({
        id: `e${index}`,
        source: id,
        target: ids[index + 1],
      })),
      presetById('horizontal-wrap'),
    )
    expect(fallback).toBe(false)
    const ys = ids.map((id) => positions.get(id)!.y)
    expect(Math.max(...ys) - Math.min(...ys)).toBeGreaterThan(120)
    expect(positions.get('A')!.x).toBeLessThan(positions.get('B')!.x)
  })

  it('引擎抛错时回退到内置分层，纵向仍往下排', async () => {
    // 前提：布局引擎直接失败，预设为纵向
    // 期望：标记回退，B 在 A 下方
    const { positions, fallback } = await layoutWithElk(
      chain,
      chainEdges,
      presetById('vertical'),
      { layout: async () => { throw new Error('elk down') } },
    )
    expect(fallback).toBe(true)
    expect(positions.get('B')!.y).toBeGreaterThan(positions.get('A')!.y)
    expect(positions.get('A')!.x).toBe(LAYOUT_ORIGIN_X)
    expect(positions.get('A')!.y).toBe(LAYOUT_ORIGIN_Y)
  })
})
