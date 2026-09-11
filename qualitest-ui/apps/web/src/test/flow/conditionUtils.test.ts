/**
 * 测谁：conditionUtils.normalizeConditionBranches
 * 边界：空 branches 补默认；已有内容不因缺 kind:else 追加结束 else
 * 单跑：pnpm test conditionUtils
 */
import { describe, expect, it } from 'vitest'

import { normalizeConditionBranches } from '@/views/project/testFlow/utils/conditionUtils'

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
})
