/**
 * 测 prefabApiDrafts：预制接口 JSON 草稿解析与提交门禁。
 * 边界：纯函数，无 UI / Pinia。
 * 单跑：pnpm test prefabApiDrafts   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import { buildLoginApi } from '@/test/project/helpers/buildTemplateRow'
import {
  applyPrefabJsonDraftChange,
  apiToJsonDrafts,
  parseRawApiObject,
  tryParseJsonText,
  validatePrefabApiDrafts,
} from '@/views/project/testProjectTemplate/utils/prefabApiDrafts'

describe('tryParseJsonText', () => {
  it('空串视为空对象', () => {
    // 前提：Tab 内容为空
    const parsed = tryParseJsonText('   ')

    // 期望：合法且值为 {}
    expect(parsed.ok).toBe(true)
    if (parsed.ok) expect(parsed.value).toEqual({})
  })

  it('非法 JSON 返回错误', () => {
    // 前提：括号不匹配
    const parsed = tryParseJsonText('{')

    // 期望：ok 为 false
    expect(parsed.ok).toBe(false)
  })
})

describe('parseRawApiObject', () => {
  it('数组或非对象拒绝', () => {
    // 前提：raw Tab 粘贴数组
    const parsed = parseRawApiObject('[]')

    // 期望：须为对象
    expect(parsed.ok).toBe(false)
    if (!parsed.ok) expect(parsed.error).toMatch(/对象/)
  })
})

describe('applyPrefabJsonDraftChange', () => {
  it('request Tab 更新 method 并回写 apis', () => {
    // 前提：选中行 requestConfig 改为 PUT
    const apis = [buildLoginApi()]
    const result = applyPrefabJsonDraftChange({
      tab: 'request',
      text: JSON.stringify({ method: 'PUT', configVersion: 1 }),
      apis,
      selectedIndex: 0,
    })

    // 期望：apis 已更新且 request 草稿格式化
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.apis[0].requestConfig.method).toBe('PUT')
      expect(result.draftPatch?.request).toContain('PUT')
    }
  })
})

describe('apiToJsonDrafts', () => {
  it('生成四 Tab 草稿', () => {
    // 前提：一条登录口
    const drafts = apiToJsonDrafts(buildLoginApi())

    // 期望：含 request/raw 等键
    expect(drafts.request).toContain('POST')
    expect(drafts.raw).toContain('/login')
  })
})

describe('validatePrefabApiDrafts', () => {
  const validDrafts = {
    request: JSON.stringify({ method: 'POST' }),
    response: '{}',
    testValue: '{}',
    raw: JSON.stringify(buildLoginApi()),
  }

  it('apis 为空时拒绝', () => {
    // 前提：未添加任何预制口
    const result = validatePrefabApiDrafts({
      apis: [],
      jsonDrafts: validDrafts,
      jsonErrors: {},
    })

    // 期望：门禁失败
    expect(result.valid).toBe(false)
    expect(result.message).toMatch(/不能为空/)
  })

  it('jsonErrors 有残留时拒绝', () => {
    // 前提：request Tab 曾报语法错
    const result = validatePrefabApiDrafts({
      apis: [buildLoginApi()],
      jsonDrafts: validDrafts,
      jsonErrors: { request: 'Unexpected token' },
    })

    // 期望：不静默提交
    expect(result.valid).toBe(false)
    expect(result.message).toMatch(/无效/)
  })

  it('草稿合法时通过', () => {
    // 前提：四 Tab 均可 parse
    const result = validatePrefabApiDrafts({
      apis: [buildLoginApi()],
      jsonDrafts: validDrafts,
      jsonErrors: {},
    })

    // 期望：允许提交
    expect(result).toEqual({ valid: true, message: '' })
  })

  it('request Tab 语法错误时拒绝', () => {
    // 前提：request 草稿非法
    const result = validatePrefabApiDrafts({
      apis: [buildLoginApi()],
      jsonDrafts: { ...validDrafts, request: '{' },
      jsonErrors: {},
    })

    // 期望：阻止提交
    expect(result.valid).toBe(false)
    expect(result.message).toMatch(/无效/)
  })
})
