/**
 * 测 fieldTypeConstraints：类型切换约束裁剪与落库清洗。
 * 边界：纯函数，无 UI / 持久化层。
 * 单跑：pnpm test fieldTypeConstraints   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import {
  pruneConstraintsForType,
  sanitizeParamRowForPersist,
  sanitizeSchemaNodeForPersist,
  supportsArrayConstraints,
  supportsFormat,
  supportsNumberConstraints,
  supportsStringConstraints
} from '@/views/project/testProject/utils/fieldTypeConstraints'

describe('fieldTypeConstraints type guards', () => {
  it('string、file、any 类型支持字符串约束，integer 不支持', () => {
    // 前提：各类型调用 supportsStringConstraints
    // 期望：string/file/any 为 true，integer 为 false
    expect(supportsStringConstraints('string')).toBe(true)
    expect(supportsStringConstraints('file')).toBe(true)
    expect(supportsStringConstraints('any')).toBe(true)
    expect(supportsStringConstraints('integer')).toBe(false)
  })

  it('integer、number 类型支持数值约束，string 不支持', () => {
    // 前提：各类型调用 supportsNumberConstraints
    // 期望：integer/number 为 true，string 为 false
    expect(supportsNumberConstraints('integer')).toBe(true)
    expect(supportsNumberConstraints('number')).toBe(true)
    expect(supportsNumberConstraints('string')).toBe(false)
  })

  it('array 类型支持数组约束，object 不支持', () => {
    // 前提：array 与 object 类型
    // 期望：array 为 true，object 为 false
    expect(supportsArrayConstraints('array')).toBe(true)
    expect(supportsArrayConstraints('object')).toBe(false)
  })

  it('string、file 类型支持 format，integer、boolean 不支持', () => {
    // 前提：各类型调用 supportsFormat
    // 期望：string/file 为 true，integer/boolean 为 false
    expect(supportsFormat('string')).toBe(true)
    expect(supportsFormat('file')).toBe(true)
    expect(supportsFormat('integer')).toBe(false)
    expect(supportsFormat('boolean')).toBe(false)
  })
})

describe('pruneConstraintsForType', () => {
  it('类型改为 integer 时清除字符串约束，保留数值约束', () => {
    // 前提：节点含字符串与数值约束，目标类型 integer
    // 期望：清除 pattern/minLength/format，保留 minimum
    const node = {
      type: 'integer',
      pattern: '^x$',
      minLength: 1,
      format: 'email',
      minimum: 0
    }
    pruneConstraintsForType(node, 'integer')
    expect(node.pattern).toBeUndefined()
    expect(node.minLength).toBeUndefined()
    expect(node.format).toBeUndefined()
    expect(node.minimum).toBe(0)
  })

  it('类型改为 string 时清除数值约束，保留字符串约束', () => {
    // 前提：节点含数值与字符串约束，目标类型 string
    // 期望：清除 minimum 等，保留 pattern
    const node = {
      type: 'string',
      minimum: 1,
      exclusiveMinimum: 0,
      multipleOf: 2,
      pattern: '^a$'
    }
    pruneConstraintsForType(node, 'string')
    expect(node.minimum).toBeUndefined()
    expect(node.exclusiveMinimum).toBeUndefined()
    expect(node.multipleOf).toBeUndefined()
    expect(node.pattern).toBe('^a$')
  })

  it('类型改为 string 时清除数组约束 minItems/maxItems', () => {
    // 前提：节点含 minItems/maxItems，目标类型 string
    // 期望：数组约束被清除
    const node = { type: 'string', minItems: 1, maxItems: 10 }
    pruneConstraintsForType(node, 'string')
    expect(node.minItems).toBeUndefined()
    expect(node.maxItems).toBeUndefined()
  })

  it('任意类型变更都会清除 enum/const/enumEnabled', () => {
    // 前提：节点含 enum/const/enumEnabled
    // 期望：三者均被清除
    const node = { type: 'string', enum: ['a'], const: 'b', enumEnabled: true }
    pruneConstraintsForType(node, 'string')
    expect(node.enum).toBeUndefined()
    expect(node.const).toBeUndefined()
    expect(node.enumEnabled).toBeUndefined()
  })

  it('平铺参数行类型为 integer 时保留 minValue，清除 pattern', () => {
    // 前提：flatParam 行含 minValue 与 pattern，类型 integer
    // 期望：保留 minValue，清除 pattern
    const row = { type: 'integer', minValue: 1, maxValue: 10, pattern: 'x' }
    pruneConstraintsForType(row, 'integer', { flatParam: true })
    expect(row.minValue).toBe(1)
    expect(row.pattern).toBeUndefined()
  })

  it('平铺参数行类型为 boolean 时清除 minValue 与 pattern', () => {
    // 前提：flatParam 行类型 boolean
    // 期望：minValue 与 pattern 均清除
    const row = { type: 'boolean', minValue: 1, pattern: 'x' }
    pruneConstraintsForType(row, 'boolean', { flatParam: true })
    expect(row.minValue).toBeUndefined()
    expect(row.pattern).toBeUndefined()
  })
})

describe('sanitizeParamRowForPersist', () => {
  it('参数行类型由 integer 改为 string 后落库不再含 minValue/maxValue', () => {
    // 前提：string 类型行仍含 minValue/maxValue
    // 期望：落库清洗后清除数值约束
    const row = { type: 'string', minValue: 1, maxValue: 99 }
    const out = sanitizeParamRowForPersist(row)
    expect(out.minValue).toBeUndefined()
    expect(out.maxValue).toBeUndefined()
  })
})

describe('sanitizeSchemaNodeForPersist', () => {
  it('落库时清除 schema 节点的 enum 与 const，保留 pattern', () => {
    // 前提：string 节点含 enum/const/pattern
    // 期望：enum/const 清除，pattern 保留
    const node = { type: 'string', enum: ['A', 'B'], const: 'A', pattern: '^x$' }
    sanitizeSchemaNodeForPersist(node)
    expect(node.enum).toBeUndefined()
    expect(node.const).toBeUndefined()
    expect(node.pattern).toBe('^x$')
  })

  it('含 properties 但未显式声明 type 时按 object 处理，清除 pattern', () => {
    // 前提：节点仅有 properties 无 type
    // 期望：按 object 清除 pattern
    const node = { properties: { a: { type: 'string' } }, pattern: 'bad' }
    sanitizeSchemaNodeForPersist(node)
    expect(node.pattern).toBeUndefined()
  })
})
