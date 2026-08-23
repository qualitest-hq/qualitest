/**
 * 测 prefabApiWorkbench：预制接口 ↔ 工作台 detail / persist 合并。
 * 边界：纯函数，无 UI / Pinia。
 * 单跑：pnpm test prefabApiWorkbench   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import { buildLoginApi } from '@/test/project/helpers/buildTemplateRow'
import {
  applyWorkbenchPersistToPrefab,
  designHintsFromText,
  designHintsToText,
  parsePersistJsonColumn,
  parseResponseConfigEditorText,
  patchAuthConfigMode,
  prefabToWorkbenchDetail,
  readAuthOverrideHeader,
  responseConfigToEditorText,
} from '@/views/project/testProjectTemplate/utils/prefabApiWorkbench'

describe('prefabToWorkbenchDetail', () => {
  it('不造假 ID 且保留对象列', () => {
    // 前提：一条带 headers 对象的登录口
    const api = buildLoginApi({
      headers: { 'X-Demo': '1' },
      cookies: { sid: 'abc' },
      preRequestScript: 'console.log(1)',
    })

    const detail = prefabToWorkbenchDetail(api)

    // 期望：无 testProjectApiId，配置仍为对象
    expect(detail.testProjectApiId).toBeUndefined()
    expect(detail.testProjectId).toBeUndefined()
    expect(detail.apiPath).toBe('/login')
    expect(detail.requestConfig).toEqual(api.requestConfig)
    expect(detail.headers).toEqual({ 'X-Demo': '1' })
    expect(detail.cookies).toEqual({ sid: 'abc' })
    expect(detail.preRequestScript).toBe('console.log(1)')
  })
})

describe('applyWorkbenchPersistToPrefab', () => {
  it('无 ID 的 persist 字符串列可合并回对象', () => {
    // 前提：工作台已拆测值，带 testValueConfig 字符串列
    const api = buildLoginApi()
    const persistPart = {
      apiPath: '/api/login',
      requestConfig: JSON.stringify({
        method: 'PUT',
        configVersion: 1,
        body: { mode: 'json', json: { schema: { type: 'object' } } },
      }),
      responseConfig: JSON.stringify({ configVersion: 1, responses: [] }),
      testValueConfig: JSON.stringify({
        request: { bodyExample: { a: 1 } },
      }),
      headers: JSON.stringify({ Authorization: 'Bearer x' }),
      cookies: JSON.stringify({}),
      preRequestScript: '',
      postRequestScript: 'return true',
    }

    const result = applyWorkbenchPersistToPrefab(api, persistPart)

    // 期望：路径与对象列已写回，测值在 testValueConfig
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.api.apiPath).toBe('/api/login')
      expect(result.api.requestConfig.method).toBe('PUT')
      expect(result.api.requestConfig.body?.json?.example).toBeUndefined()
      expect(result.api.testValueConfig.request.bodyExample).toEqual({ a: 1 })
      expect(result.api.headers).toEqual({ Authorization: 'Bearer x' })
      expect(result.api.cookies).toEqual({})
      expect(result.api.postRequestScript).toBe('return true')
      expect(result.api.apiName).toBe('登录')
      expect(result.api.authConfig).toEqual({ mode: 'none' })
    }
  })

  it('persistPart.error 时拒绝', () => {
    // 前提：工作台报请求体 JSON 非法
    const result = applyWorkbenchPersistToPrefab(buildLoginApi(), {
      error: '请求体 JSON 格式无效',
    })

    // 期望：不覆盖预制口
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.error).toMatch(/请求体/)
  })

  it('缺少 testValueConfig 时拒绝', () => {
    const result = applyWorkbenchPersistToPrefab(buildLoginApi(), {
      apiPath: '/login',
      requestConfig: '{}',
      headers: '{}',
      cookies: '{}',
    })
    expect(result.ok).toBe(false)
    if (!result.ok) expect(result.error).toMatch(/testValueConfig/)
  })

  it('非法 requestConfig JSON 回退为空对象仍合并成功', () => {
    // 前提：requestConfig 不是合法 JSON，但已有 testValueConfig
    const result = applyWorkbenchPersistToPrefab(buildLoginApi(), {
      apiPath: '/login',
      requestConfig: '{',
      headers: '{}',
      cookies: '{}',
      testValueConfig: '{}',
      responseConfig: '{}',
    })

    // 期望：parse 失败用 {}，整次合并仍 ok
    expect(result.ok).toBe(true)
    if (result.ok) expect(result.api.requestConfig).toEqual({})
  })
})

describe('parsePersistJsonColumn', () => {
  it('对象深拷贝、非法串回 fallback', () => {
    // 前提：对象与非法字符串
    expect(parsePersistJsonColumn({ a: 1 })).toEqual({ a: 1 })
    expect(parsePersistJsonColumn('{', { x: 1 })).toEqual({ x: 1 })
  })
})

describe('responseConfig 文本互转', () => {
  it('空值得到默认 responses 结构', () => {
    // 前提：无 responseConfig
    const text = responseConfigToEditorText(null)
    const parsed = JSON.parse(text)

    // 期望：可被面板消费
    expect(parsed.responses).toEqual([])
  })

  it('非法 JSON 解析失败', () => {
    // 前提：响应 Tab 粘贴坏 JSON
    const result = parseResponseConfigEditorText('{')

    // 期望：拒绝写回
    expect(result.ok).toBe(false)
  })
})

describe('designHints / auth override', () => {
  it('hints 换行互转', () => {
    // 前提：两行提示
    const text = designHintsToText({ hints: ['a', 'b'] })
    expect(text).toBe('a\nb')
    expect(designHintsFromText('a\n\nb')).toEqual({ hints: ['a', 'b'] })
  })

  it('override 写入 header 且去掉 authProfileId', () => {
    // 前提：切到自定义鉴权
    const next = patchAuthConfigMode(
      { mode: 'inherit', authProfileId: 'adminBearer' },
      'override',
      { headerName: 'X-Token', valueTemplate: '{{flow.token}}' },
    )

    // 期望：无 Profile id，有 header
    expect(next.mode).toBe('override')
    expect(next.authProfileId).toBeUndefined()
    expect(next.header).toEqual({ name: 'X-Token', valueTemplate: '{{flow.token}}' })
    expect(readAuthOverrideHeader(next).headerName).toBe('X-Token')
  })
})
