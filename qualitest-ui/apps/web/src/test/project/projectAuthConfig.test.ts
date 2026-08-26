/**
 * 测 projectAuthConfig：auth_config 表单 parse / build / validate。
 * 边界：纯函数，无 UI / 持久化层。
 * 单跑：pnpm test projectAuthConfig   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import { buildAdminBearerProfile, buildLoginApi } from '@/test/project/helpers/buildTemplateRow'
import {
  buildAuthConfigObject,
  buildAuthConfigPayload,
  emptyAuthForm,
  parseAuthConfig,
  parseJsonMaybe,
  splitPathLines,
  validateAuthForm,
} from '@/views/project/testProject/utils/projectAuthConfig'

describe('parseAuthConfig', () => {
  it('解析扁平头、credentialApi 与值模板中的 asset 占位', () => {
    // 前提：库中为新版 authProfiles 结构（无 loginHint）
    const raw = {
      authProfiles: [buildAdminBearerProfile()],
    }

    const form = parseAuthConfig(raw)

    // 期望：表单行含 pathPrefix、credential 与 asset 值模板；不含 loginHint 字段
    expect(form.profiles).toHaveLength(1)
    expect(form.profiles[0].headerName).toBe('Authorization')
    expect(form.profiles[0].credentialPath).toBe('/login')
    expect(form.profiles[0].valueTemplate).toBe('Bearer {{asset.adminAuth.token}}')
    expect(form.profiles[0].loginFlowKey).toBeUndefined()
    expect(form.profiles[0].pathPrefixText).toBe('/system/')
    expect(form.profiles[0].apis[0].authMode).toBe('none')
  })

  it('有 Profile 但 apis 为空时 needsAuthTemplateHint 为 true', () => {
    // 前提：存量仅有 Profile 无预制口
    const raw = {
      authProfiles: [{
        id: 'legacy',
        headerName: 'Authorization',
        headerValueTemplate: 'Bearer {{asset.adminAuth.token}}',
        apis: [],
      }],
    }

    const form = parseAuthConfig(raw)

    // 期望：提示用户从项目模板补齐预制接口
    expect(form.needsAuthTemplateHint).toBe(true)
  })

  it('options.needsAuthTemplateHint 优先于本地推断', () => {
    // 前提：后端显式返回 needsAuthTemplateHint
    const form = parseAuthConfig({ authProfiles: [] }, { needsAuthTemplateHint: true })

    // 期望：保留后端标记
    expect(form.needsAuthTemplateHint).toBe(true)
  })
})

describe('buildAuthConfigPayload', () => {
  it('round-trip 保留 authProfiles 核心字段且不写 loginHint', () => {
    // 前提：完整表单行（值模板用 asset 占位）
    const form = parseAuthConfig({
      authProfiles: [{
        id: 'ruoyiBearer',
        name: 'RuoYi Bearer',
        headerName: 'Authorization',
        headerValueTemplate: 'Bearer {{asset.adminAuth.token}}',
        credentialApi: { method: 'POST', path: '/login' },
        apis: [buildLoginApi()],
      }],
    })

    const obj = buildAuthConfigObject(form)

    // 期望：写出仅含 authProfiles，无 loginHint
    expect(obj.authProfiles[0].loginHint).toBeUndefined()
    expect(obj.authProfiles[0].headerValueTemplate).toBe('Bearer {{asset.adminAuth.token}}')
    expect(obj.authProfiles[0].credentialApi.path).toBe('/login')
    expect(JSON.parse(buildAuthConfigPayload(form)).authProfiles[0].id).toBe('ruoyiBearer')
  })

  it('pathPrefix 禁止单独 / 时 validateAuthForm 失败', () => {
    // 前提：pathPrefix 写了根路径
    const form = emptyAuthForm()
    form.profiles.push({
      id: 'bad',
      name: '',
      pathPrefixText: '/',
      headerName: 'Authorization',
      valueTemplate: 'Bearer {{asset.adminAuth.token}}',
      credentialMethod: 'POST',
      credentialPath: '',
      apis: [],
    })

    // 期望：可读校验错误
    expect(validateAuthForm(form)).toMatch(/禁止/)
    expect(() => buildAuthConfigObject(form)).toThrow(/禁止/)
  })

  it('空 Profile 列表返回 null 对象', () => {
    // 前提：无任何 Profile
    expect(buildAuthConfigObject(emptyAuthForm())).toBeNull()
    expect(buildAuthConfigPayload(emptyAuthForm())).toBe('{}')
  })
})

describe('parseJsonMaybe / splitPathLines', () => {
  it('解析 JSON 与拆分 pathPrefix 文本', () => {
    expect(parseJsonMaybe('{"a":1}')).toEqual({ a: 1 })
    expect(parseJsonMaybe('{')).toBeNull()
    expect(splitPathLines('/api/\n/mall/, /system/')).toEqual(['/api/', '/mall/', '/system/'])
  })
})
