/**
 * 项目模板表单工具单测：解析、method 同步、提交校验。
 * 纯函数，无 UI / 持久化。
 */
import { describe, expect, it } from 'vitest'

import { buildLoginApi, buildTemplateRow } from '@/test/project/helpers/buildTemplateRow'
import {
  buildLoginGraphJson,
  emptyPrefabricatedApi,
  emptyTemplateForm,
  formToPayload,
  parseApis,
  pathPrefixTextToMatchConfig,
  replaceApiAtIndex,
  resolveApiMethod,
  setApiMethod,
  syncMethodFromRequestConfig,
  templateToForm,
  validateApis,
  validateParams,
} from '@/views/project/testProjectTemplate/utils/templateForm'

describe('parseApis', () => {
  it('解析 JSON 字符串为对象数组', () => {
    // 前提：库列 template_apis 为 JSON 字符串
    const raw = JSON.stringify([buildLoginApi()])

    const list = parseApis(raw)

    // 期望：得到非空数组且字段可读
    expect(list).toHaveLength(1)
    expect(list[0].apiName).toBe('登录')
    expect(list[0].apiPath).toBe('/login')
  })
})

describe('emptyPrefabricatedApi', () => {
  it('默认 POST 且 authConfig.mode=none', () => {
    // 前提：新建预制接口
    const api = emptyPrefabricatedApi()

    // 期望：登录口常用默认值
    expect(resolveApiMethod(api)).toBe('POST')
    expect(api.authConfig.mode).toBe('none')
    expect(api.requestConfig.body.mode).toBe('json')
  })
})

describe('method 双向同步', () => {
  it.each([
    {
      title: '表单改 method 写入 requestConfig',
      run: () => setApiMethod(emptyPrefabricatedApi(), 'GET'),
      expected: 'GET',
    },
    {
      title: '请求 JSON 含 method 时回写 api',
      run: () => {
        const api = emptyPrefabricatedApi()
        return syncMethodFromRequestConfig(api, { ...api.requestConfig, method: 'PUT' })
      },
      expected: 'PUT',
    },
  ])('$title', ({ run, expected }) => {
    // 前提：见 title
    const next = run()

    // 期望：表格可读 method 与 requestConfig 一致
    expect(resolveApiMethod(next)).toBe(expected)
  })
})

describe('replaceApiAtIndex', () => {
  it('原始 JSON 合法时替换当前行', () => {
    // 前提：一条预制口
    const apis = [{ apiName: '旧', apiPath: '/old', requestConfig: { method: 'GET' } }]
    const nextApi = { apiName: '新', apiPath: '/new', requestConfig: { method: 'POST' } }

    const list = replaceApiAtIndex(apis, 0, nextApi)

    // 期望：下标 0 被整条替换
    expect(list[0].apiName).toBe('新')
    expect(resolveApiMethod(list[0])).toBe('POST')
  })

  it('越界或非对象时抛错', () => {
    // 前提：非法下标或 raw 非对象
    const apis = [buildLoginApi()]

    // 期望：可读错误
    expect(() => replaceApiAtIndex(apis, 2, buildLoginApi())).toThrow('接口索引无效')
    expect(() => replaceApiAtIndex(apis, 0, [])).toThrow('预制接口须为 JSON 对象')
  })
})

describe('pathPrefixTextToMatchConfig', () => {
  it('禁止单独 /', () => {
    // 前提：pathPrefix 写了根路径
    expect(() => pathPrefixTextToMatchConfig('/')).toThrow('pathPrefix 禁止使用 "/"')
  })
})

describe('validateApis', () => {
  it('空数组拒绝提交', () => {
    // 前提：未配置任何预制口
    expect(() => validateApis([])).toThrow('预制接口须为非空数组')
  })
})

describe('validateParams', () => {
  it('只保留 flow/env/asset，丢弃旧 value/extract/assert', () => {
    // 前提：混有新口径与旧行
    const rows = [
      { kind: 'flow', name: 'token', value: '' },
      { kind: 'env', name: 'timeout', value: '5000' },
      { kind: 'asset', name: 'clientAuth', value: { mobile: '13800000001' } },
      { kind: 'assert', left: 'http.body.code', operator: 'eq', right: '0' },
      { kind: 'value', name: 'username', value: 'admin', bind: { method: 'POST', path: '/login' } },
    ]

    const next = validateParams(rows)

    // 期望：仅三项新口径
    expect(next).toHaveLength(3)
    expect(next.map((r) => r.kind)).toEqual(['flow', 'env', 'asset'])
  })
})

describe('buildLoginGraphJson', () => {
  it('补齐 timeoutMs / successCheck / scenarios，并默认抽到 asset', () => {
    // 前提：组装登录骨架（不传 flowKey，走内置 asset 口径）
    const graph = buildLoginGraphJson({
      method: 'POST',
      apiPath: '/login',
      from: 'body',
      expr: '$.token',
    })

    // 期望：与真实 HTTP 节点默认字段对齐，抽取写 asset.adminAuth.token
    expect(graph.nodes[0].data.apiPath).toBe('/login')
    expect(graph.nodes[0].data.timeoutMs).toBe(30000)
    expect(graph.nodes[0].data.successCheck).toEqual({ mode: 'inherit' })
    expect(graph.nodes[0].data.extracts[0]).toMatchObject({
      scope: 'asset',
      entryKey: 'adminAuth',
      fieldPath: 'token',
      expr: '$.token',
    })
    expect(graph.meta.layout).toBe('manual')
    expect(graph.meta.scenarios).toHaveLength(1)
    expect(graph.meta.viewport).toEqual({ x: 40, y: 40, zoom: 1 })
    expect(graph.meta.flowOutputs).toEqual([])
  })

  it('显式 flowKey 时仍可写出 flow 抽取', () => {
    // 前提：兼容旧调用传 flowKey
    const graph = buildLoginGraphJson({
      method: 'POST',
      apiPath: '/login',
      from: 'body',
      expr: '$.token',
      flowKey: 'token',
    })

    // 期望：scope=flow，name=token
    expect(graph.nodes[0].data.extracts[0]).toMatchObject({
      scope: 'flow',
      name: 'token',
    })
    expect(graph.meta.flowOutputs).toEqual([{ name: 'token' }])
  })
})

describe('emptyTemplateForm', () => {
  it('默认 templateApis/templateParams/templateFlows 为空数组', () => {
    // 前提：新增模板
    const form = emptyTemplateForm()

    // 期望：由面板引导用户新增预制口；参数与流可空
    expect(form.templateApis).toEqual([])
    expect(form.templateParams).toEqual([])
    expect(form.templateFlows).toEqual([])
  })
})

describe('templateToForm / formToPayload', () => {
  it('round-trip 保留 templateApis 数组且不强制 header', () => {
    // 前提：详情含一条登录口
    const row = buildTemplateRow()

    const form = templateToForm(row)
    const payload = formToPayload(form)

    // 期望：提交体 templateApis 仍为合法 JSON 字符串；params/flows 为数组 JSON
    expect(form.templateApis).toHaveLength(1)
    expect(form.pathPrefixText).toBe('/api/')
    const parsed = JSON.parse(payload.templateApis)
    expect(parsed[0].apiPath).toBe('/login')
    expect(payload.templateName).toBe('测试模板')
    expect(JSON.parse(payload.templateParams)).toEqual([])
    expect(JSON.parse(payload.templateFlows)).toEqual([])
    expect(payload.headerName).toBeUndefined()
    // 雪花 ID 保持字符串，避免 Number 精度丢失
    expect(payload.testProjectTemplateId).toBe('1')
  })

  it('雪花 ID 不以 Number 提交', () => {
    // 前提：克隆模板 ID 超过 MAX_SAFE_INTEGER
    const snowflake = '2091427833602859008'
    const form = templateToForm(buildTemplateRow({ testProjectTemplateId: snowflake }))

    const payload = formToPayload(form)

    // 期望：原样字符串，Number() 会变成错误值
    expect(payload.testProjectTemplateId).toBe(snowflake)
    expect(payload.testProjectTemplateId).not.toBe(String(Number(snowflake)))
  })

  it('pathPrefix 非法时 formToPayload 失败', () => {
    // 前提：apis 合法但 pathPrefix 为 /
    const form = templateToForm(buildTemplateRow())
    form.pathPrefixText = '/'

    // 期望：提交前抛错
    expect(() => formToPayload(form)).toThrow('pathPrefix 禁止使用 "/"')
  })
})
