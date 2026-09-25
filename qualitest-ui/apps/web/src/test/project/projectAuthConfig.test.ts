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
  it('解析扁平头与 asset 占位', () => {
    // 前提：库中 Profile 为扁平头 + asset 值模板 + 预制登录口
    const raw = {
      authProfiles: [buildAdminBearerProfile()],
    }

    const form = parseAuthConfig(raw)

    // 期望：表单行含 pathPrefix 与 asset 值模板
    expect(form.profiles).toHaveLength(1)
    expect(form.profiles[0].headerName).toBe('Authorization')
    expect(form.profiles[0].valueTemplate).toBe('Bearer {{asset.adminAuth.token}}')
    expect(form.profiles[0].pathPrefixText).toBe('/system/')
    expect(form.profiles[0].apis[0].authMode).toBe('none')
  })

  it('有 Profile 但 apis 为空时 needsAuthTemplateHint 为 true', () => {
    // 前提：仅有 Profile 无预制口
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
  it('round-trip 保留 authProfiles 核心字段', () => {
    // 前提：完整表单行（值模板用 asset 占位）
    const form = parseAuthConfig({
      authProfiles: [{
        id: 'ruoyiBearer',
        name: 'RuoYi Bearer',
        headerName: 'Authorization',
        headerValueTemplate: 'Bearer {{asset.adminAuth.token}}',
        apis: [buildLoginApi()],
      }],
    })

    const obj = buildAuthConfigObject(form)

    // 期望：写出 authProfiles，托管头与响应约定齐全
    const payload = buildAuthConfigPayload(form)
    expect(obj.authProfiles[0].headerValueTemplate).toBe('Bearer {{asset.adminAuth.token}}')
    expect(JSON.parse(payload).authProfiles[0].id).toBe('ruoyiBearer')
    expect(obj.authProfiles[0].responseConvention).toEqual({
      codePath: 'code',
      successValues: [200],
      messagePath: 'msg',
      dataPath: 'data',
    })
  })

  it('round-trip 保留 Profile.responseConvention 四字段', () => {
    const form = parseAuthConfig({
      authProfiles: [{
        id: 'clientBearer',
        name: '客户端',
        headerName: 'Authorization',
        headerValueTemplate: 'Bearer {{asset.clientAuth.token}}',
        responseConvention: {
          codePath: 'errno',
          successValues: [0, 1],
          messagePath: 'errmsg',
          dataPath: 'result',
        },
        apis: [],
      }],
    })

    expect(form.profiles[0].codePath).toBe('errno')
    expect(form.profiles[0].successValuesText).toBe('0,1')
    expect(form.profiles[0].messagePath).toBe('errmsg')
    expect(form.profiles[0].dataPath).toBe('result')

    const obj = buildAuthConfigObject(form)
    expect(obj.authProfiles[0].responseConvention).toEqual({
      codePath: 'errno',
      successValues: [0, 1],
      messagePath: 'errmsg',
      dataPath: 'result',
    })
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
