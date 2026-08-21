/**
 * 测 jsonSchemaTree：从 schema default 生成示例，并与请求示例合并。
 * 边界：仅显式 default、空串填充、手改 JSON 不被空 default 冲掉。
 * 单跑：pnpm test jsonSchemaExampleSync
 */
import { describe, expect, it } from 'vitest'

import {
  buildExampleFromSchemaDefaults,
  mergeJsonFillEmpty,
  mergeJsonPreferFiller
} from '@/views/project/testProject/utils/jsonSchemaTree'

describe('buildExampleFromSchemaDefaults', () => {
  it('仅显式 default 时输出带值的叶子，无 default 的字段不出现', () => {
    // 前提：object schema，mobile 有 default，password 无
    // 期望：只含 mobile
    const schema = {
      type: 'object',
      properties: {
        mobile: {type: 'string', default: '13800000001'},
        password: {type: 'string'}
      }
    }
    expect(buildExampleFromSchemaDefaults(schema, {onlyExplicitDefaults: true})).toEqual({
      mobile: '13800000001'
    })
  })

  it('非 onlyExplicit 时无 default 的 string 填空串以保留键形', () => {
    // 前提：password 无 default
    // 期望：password 为 ''
    const schema = {
      type: 'object',
      properties: {
        password: {type: 'string'}
      }
    }
    expect(buildExampleFromSchemaDefaults(schema)).toEqual({password: ''})
  })
})

describe('mergeJsonFillEmpty', () => {
  it('用 schema default 填补请求示例中的空串', () => {
    // 前提：example 空串 + filler 有登录字段
    // 期望：空位被填上，已有非空值保留
    const example = {mobile: '', password: '', nick: 'keep'}
    const filler = {mobile: '13800000001', password: 'Test@123456'}
    expect(mergeJsonFillEmpty(example, filler)).toEqual({
      mobile: '13800000001',
      password: 'Test@123456',
      nick: 'keep'
    })
  })
})

describe('mergeJsonPreferFiller', () => {
  it('数据结构改参后覆盖对应键，未出现在 filler 的键保留', () => {
    // 前提：example 已有值；filler 仅更新 password
    // 期望：password 被覆盖，mobile 不变
    const example = {mobile: '13800000001', password: 'old'}
    const filler = {password: 'Test@123456'}
    expect(mergeJsonPreferFiller(example, filler)).toEqual({
      mobile: '13800000001',
      password: 'Test@123456'
    })
  })
})
