/**
 * 测谁：conditionUtils.normalizeConditionBranches / getConditionEdgeHandle / bindConditionBranchTarget
 * 边界：空 branches 补默认；缺 sourceHandle 不绑分支
 * 单跑：pnpm test conditionUtils
 */
import { describe, expect, it } from 'vitest'

import {
  bindConditionBranchTarget,
  getConditionEdgeHandle,
  normalizeConditionBranches,
} from '@/views/project/testFlow/utils/conditionUtils'

describe('normalizeConditionBranches', () => {
  it('空 branches 写入默认 IF+ELSE', () => {
    // 前提：data.branches 为空
    const data: Record<string, unknown> = { branches: [] }
    // 期望：补默认两条
    normalizeConditionBranches(data)
    const branches = data.branches as Array<{ kind: string }>
    expect(branches).toHaveLength(2)
    expect(branches.map((b) => b.kind)).toEqual(['if', 'else'])
  })

  it('已有两条无 kind:else 的分支时不追加第三条', () => {
    // 前提：AI 脏数据用 name 冒充，无 kind:else
    const data: Record<string, unknown> = {
      branches: [
        { id: 'a', name: 'IF', conditions: [{ left: 'asset.x', operator: 'exists' }] },
        { id: 'b', name: 'ELSE', conditions: [] },
      ],
    }
    // 期望：不 push 无出口的假 else
    normalizeConditionBranches(data)
    expect(data.branches).toHaveLength(2)
  })

  it('缺省 branches 字段时写入默认 IF+ELSE', () => {
    // 前提：无 branches
    const data: Record<string, unknown> = {}
    // 期望：默认两条
    normalizeConditionBranches(data)
    expect(data.branches).toHaveLength(2)
  })

  it('getConditionEdgeHandle：无 target 匹配时返回 out', () => {
    // 前提：condition 有分支但 edge.target 未绑到任何分支
    // 期望：返回通用 out
    const src = {
      type: 'condition',
      data: {
        branches: [
          { id: 'if1', kind: 'if' },
          { id: 'else1', kind: 'else' },
        ],
      },
    }
    expect(getConditionEdgeHandle(src, { target: 'ghost' })).toBe('out')
  })

  it('bindConditionBranchTarget：缺 sourceHandle 不绑', () => {
    // 前提：边无 sourceHandle
    // 期望：不修改 branches
    const data: Record<string, unknown> = {
      branches: [
        { id: 'if1', kind: 'if' },
        { id: 'else1', kind: 'else' },
      ],
    }
    expect(bindConditionBranchTarget(data, { target: '9001' })).toBe(false)
    expect((data.branches as Array<{ target?: string }>)[0].target).toBeUndefined()
  })
})
