/**
 * 测谁：点中节点时，哪些连线要流动，以及流动样式怎么叠到原有描边上。
 * 边界：出边、入边、无关边、缺边、已有虚线、普通灰线。
 * 单跑：pnpm test flowEdgeFlow   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import { applyEdgeFlow, edgeTouchesNode } from '@/views/project/testFlow/utils/flowEdgeFlow'

describe('edgeTouchesNode', () => {
  it('出边和入边算接到该节点', () => {
    // 前提：一条从 a 到 b 的边
    // 期望：a、b 都算接到；c 不算；没有边或没有节点 id 也不算
    const edge = { source: 'a', target: 'b' }
    expect(edgeTouchesNode(edge, 'a')).toBe(true)
    expect(edgeTouchesNode(edge, 'b')).toBe(true)
    expect(edgeTouchesNode(edge, 'c')).toBe(false)
    expect(edgeTouchesNode(undefined, 'a')).toBe(false)
    expect(edgeTouchesNode(edge, null)).toBe(false)
  })
})

describe('applyEdgeFlow', () => {
  it('未流动时原样返回', () => {
    // 前提：普通灰线，且当前不流动
    // 期望：描边不变，没有动画
    const style = { stroke: '#94a3b8', strokeWidth: 1.5 }
    expect(applyEdgeFlow(style, false)).toEqual(style)
  })

  it('普通灰线流动时改成蓝色虚线并带动画', () => {
    // 前提：普通灰线，且接到选中节点
    // 期望：改成蓝色、虚线，并带上流动动画
    const next = applyEdgeFlow({ stroke: '#94a3b8', strokeWidth: 1.5 }, true)
    expect(next.stroke).toBe('#0b6edc')
    expect(next.strokeWidth).toBe(2.5)
    expect(next.strokeDasharray).toBe('8 6')
    expect(next.animation).toContain('qualitest-edge-flow')
  })

  it('已有虚线时只让它动，不改这段虚线', () => {
    // 前提：待确认新增边自己已有紫色虚线
    // 期望：颜色和虚线保留，只补上流动动画
    const next = applyEdgeFlow(
      { stroke: 'rgba(124, 58, 237, 0.9)', strokeWidth: 2.5, strokeDasharray: '6 4' },
      true,
    )
    expect(next.stroke).toContain('124, 58, 237')
    expect(next.strokeDasharray).toBe('6 4')
    expect(next.animation).toContain('qualitest-edge-flow')
  })
})
