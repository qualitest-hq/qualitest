/**
 * 测谁：nodes-change 是否应标脏 / 占写锁。
 * 边界：position/add/remove 算内容；dimensions/select 不算。
 * 单跑：pnpm test nodeChangeAffectsPersist（在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'
import type { NodeChange } from '@vue-flow/core'

import { nodeChangeAffectsPersist } from '../../views/project/testFlow/utils/nodeChangeAffectsPersist'

describe('nodeChangeAffectsPersist', () => {
  // 前提：空或仅尺寸/选中变更。
  // 期望：不算内容变更。
  it('dimensions / select 不标脏', () => {
    expect(nodeChangeAffectsPersist(undefined)).toBe(false)
    expect(nodeChangeAffectsPersist([])).toBe(false)
    const dims = [{ type: 'dimensions', id: 'n1', dimensions: { width: 10, height: 10 } }] as NodeChange[]
    const sel = [{ type: 'select', id: 'n1', selected: true }] as NodeChange[]
    expect(nodeChangeAffectsPersist(dims)).toBe(false)
    expect(nodeChangeAffectsPersist(sel)).toBe(false)
  })

  // 前提：含拖拽落点或增删节点。
  // 期望：算内容变更。
  it('position / add / remove 标脏', () => {
    const pos = [{ type: 'position', id: 'n1', position: { x: 1, y: 2 } }] as NodeChange[]
    const add = [{ type: 'add', item: { id: 'n2', position: { x: 0, y: 0 } } }] as NodeChange[]
    const rem = [{ type: 'remove', id: 'n1' }] as NodeChange[]
    expect(nodeChangeAffectsPersist(pos)).toBe(true)
    expect(nodeChangeAffectsPersist(add)).toBe(true)
    expect(nodeChangeAffectsPersist(rem)).toBe(true)
    expect(nodeChangeAffectsPersist([...dimsSafe(), ...pos])).toBe(true)
  })
})

function dimsSafe(): NodeChange[] {
  return [{ type: 'dimensions', id: 'n1', dimensions: { width: 1, height: 1 } }] as NodeChange[]
}
