/**
 * 测 peelTestValueConfig：结构 ↔ testValueConfig 剥离与叠回。
 * 边界：空测值、仅 bodyExample、paramDefaults 补行。
 * 单跑：pnpm test peelTestValueConfig
 */
import { describe, expect, it } from 'vitest'
import {
  applyTestValuesToStructure,
  peelTestValuesFromStructure,
} from '@/views/project/testProject/utils/peelTestValueConfig'
import { buildRequestWorkbenchStateFromDetail } from '@/views/project/testProject/utils/apiDetailRequestWorkbench'

describe('peelTestValuesFromStructure', () => {
  it('剥离 query value 与 body example', () => {
    // 前提：结构里带 query value 与 body.json.example
    // 期望：结构侧删掉测值，testValueConfig 收下
    const { requestConfig, testValueConfig } = peelTestValuesFromStructure(
      {
        method: 'POST',
        queryParams: [{ name: 'q', value: 'x', type: 'string' }],
        body: { mode: 'json', json: { schema: { type: 'object' }, example: { a: 1 } } },
      },
      { configVersion: 1, responses: [] },
      null,
    )
    expect(requestConfig.queryParams[0].value).toBeUndefined()
    expect(requestConfig.body.json.example).toBeUndefined()
    expect(testValueConfig.request.paramDefaults.q).toBe('x')
    expect(testValueConfig.request.bodyExample).toEqual({ a: 1 })
  })

  it('剥离响应 example 到 examplesById', () => {
    // 前提：响应条目带 id 与 example
    // 期望：example 进 examplesById，结构侧删除
    const { responseConfig, testValueConfig } = peelTestValuesFromStructure(
      {},
      {
        responses: [{ id: 'r1', example: { code: 0 }, schema: { type: 'object' } }],
      },
      null,
    )
    expect(responseConfig.responses[0].example).toBeUndefined()
    expect(testValueConfig.response.examplesById.r1).toEqual({ code: 0 })
  })
})

describe('applyTestValuesToStructure', () => {
  it('剥离开再叠回后 example 与 value 恢复', () => {
    // 前提：先 peel，再对干净结构 apply 同一份 testValueConfig
    // 期望：body.json.example 与 query value 回到测值
    const original = {
      method: 'POST',
      queryParams: [{ name: 'q', value: 'x', type: 'string' }],
      body: { mode: 'json', json: { schema: { type: 'object' }, example: { a: 1 } } },
    }
    const { requestConfig, testValueConfig } = peelTestValuesFromStructure(
      original,
      { configVersion: 1, responses: [] },
      null,
    )
    applyTestValuesToStructure(requestConfig, testValueConfig)
    expect(requestConfig.queryParams[0].value).toBe('x')
    expect(requestConfig.body.json.example).toEqual({ a: 1 })
  })

  it('bodyExample 写入 json.example 并在 mode 为空时切到 json', () => {
    // 前提：结构无 body mode，测值仅有 bodyExample
    // 期望：mode=json 且 example 写回
    const rc = { method: 'POST', queryParams: [], body: {} }
    applyTestValuesToStructure(rc, {
      request: { bodyExample: { username: 'admin', password: 'admin123' } },
    })
    expect(rc.body.mode).toBe('json')
    expect(rc.body.json.example).toEqual({ username: 'admin', password: 'admin123' })
  })
})

describe('buildRequestWorkbenchStateFromDetail 合并测值', () => {
  it('加载时把 bodyExample 叠进草稿', () => {
    // 前提：详情 requestConfig 无 example，testValueConfig 有 bodyExample
    // 期望：草稿 body.json.example 有测值（Body 徽章可读）
    const { draftRequestConfig } = buildRequestWorkbenchStateFromDetail({
      apiPath: '/login',
      requestConfig: {
        configVersion: 1,
        method: 'POST',
        queryParams: [],
        pathParams: [],
        body: {
          mode: 'json',
          json: {
            schema: {
              type: 'object',
              properties: { username: { type: 'string' }, password: { type: 'string' } },
            },
          },
        },
      },
      testValueConfig: {
        request: { bodyExample: { username: 'admin', password: 'admin123' } },
      },
      headers: {},
      cookies: {},
    })
    expect(draftRequestConfig.body.json.example).toEqual({
      username: 'admin',
      password: 'admin123',
    })
  })
})
