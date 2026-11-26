/**
 * 测 enumerateSimulatePaths：condition 多出边是分支，非 condition 多出边是隐式扇出（各臂都跑）。
 * 边界：纯函数，不评估条件、不发起 HTTP。
 * 单跑：pnpm test simulatePaths   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import { enumerateSimulatePaths } from '@/views/project/testFlow/utils/simulatePaths'

describe('enumerateSimulatePaths', () => {
  it('condition 多出边按分支展示，不标扇出', () => {
    // 前提：condition 节点分出两条互斥臂
    // 期望：两条路径都不带「扇出各臂」前缀
    const edges = [
      { id: 'e1', source: 'c1', target: 'n1' },
      { id: 'e2', source: 'c1', target: 'n2' },
    ]
    const paths = enumerateSimulatePaths(edges, 'c1', new Map(), new Map([['c1', 'condition']]))

    expect(paths).toHaveLength(2)
    expect(paths.every((p) => p.fanout === false)).toBe(true)
    expect(paths.some((p) => p.label.includes('扇出各臂'))).toBe(false)
  })

  it('非 condition 多出边标为扇出各臂', () => {
    // 前提：http 节点带两条出边（隐式扇出，两臂都会跑）
    // 期望：路径 label 带「扇出各臂」且 fanout=true
    const edges = [
      { id: 'e1', source: 'h1', target: 'n1' },
      { id: 'e2', source: 'h1', target: 'n2' },
    ]
    const paths = enumerateSimulatePaths(edges, 'h1', new Map(), new Map([['h1', 'http']]))

    expect(paths).toHaveLength(2)
    expect(paths.every((p) => p.fanout === true)).toBe(true)
    expect(paths.every((p) => p.label.includes('扇出各臂'))).toBe(true)
  })
})
