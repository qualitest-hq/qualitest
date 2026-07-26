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
  it('supportsStringConstraints', () => {
    expect(supportsStringConstraints('string')).toBe(true)
    expect(supportsStringConstraints('file')).toBe(true)
    expect(supportsStringConstraints('any')).toBe(true)
    expect(supportsStringConstraints('integer')).toBe(false)
  })

  it('supportsNumberConstraints', () => {
    expect(supportsNumberConstraints('integer')).toBe(true)
    expect(supportsNumberConstraints('number')).toBe(true)
    expect(supportsNumberConstraints('string')).toBe(false)
  })

  it('supportsArrayConstraints', () => {
    expect(supportsArrayConstraints('array')).toBe(true)
    expect(supportsArrayConstraints('object')).toBe(false)
  })

  it('supportsFormat', () => {
    expect(supportsFormat('string')).toBe(true)
    expect(supportsFormat('file')).toBe(true)
    expect(supportsFormat('integer')).toBe(false)
    expect(supportsFormat('boolean')).toBe(false)
  })
})

describe('pruneConstraintsForType', () => {
  it('string→integer removes string constraints', () => {
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

  it('integer→string removes number constraints', () => {
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

  it('array→string removes minItems/maxItems', () => {
    const node = { type: 'string', minItems: 1, maxItems: 10 }
    pruneConstraintsForType(node, 'string')
    expect(node.minItems).toBeUndefined()
    expect(node.maxItems).toBeUndefined()
  })

  it('always removes enum/const', () => {
    const node = { type: 'string', enum: ['a'], const: 'b', enumEnabled: true }
    pruneConstraintsForType(node, 'string')
    expect(node.enum).toBeUndefined()
    expect(node.const).toBeUndefined()
    expect(node.enumEnabled).toBeUndefined()
  })

  it('flat param uses minValue/maxValue', () => {
    const row = { type: 'integer', minValue: 1, maxValue: 10, pattern: 'x' }
    pruneConstraintsForType(row, 'integer', { flatParam: true })
    expect(row.minValue).toBe(1)
    expect(row.pattern).toBeUndefined()

    const row2 = { type: 'boolean', minValue: 1, pattern: 'x' }
    pruneConstraintsForType(row2, 'boolean', { flatParam: true })
    expect(row2.minValue).toBeUndefined()
    expect(row2.pattern).toBeUndefined()
  })
})

describe('sanitizeParamRowForPersist', () => {
  it('integer row changed to string has no minValue', () => {
    const row = { type: 'string', minValue: 1, maxValue: 99 }
    const out = sanitizeParamRowForPersist(row)
    expect(out.minValue).toBeUndefined()
    expect(out.maxValue).toBeUndefined()
  })
})

describe('sanitizeSchemaNodeForPersist', () => {
  it('strips enum and const from schema node', () => {
    const node = { type: 'string', enum: ['A', 'B'], const: 'A', pattern: '^x$' }
    sanitizeSchemaNodeForPersist(node)
    expect(node.enum).toBeUndefined()
    expect(node.const).toBeUndefined()
    expect(node.pattern).toBe('^x$')
  })

  it('infers object type from properties', () => {
    const node = { properties: { a: { type: 'string' } }, pattern: 'bad' }
    sanitizeSchemaNodeForPersist(node)
    expect(node.pattern).toBeUndefined()
  })
})
