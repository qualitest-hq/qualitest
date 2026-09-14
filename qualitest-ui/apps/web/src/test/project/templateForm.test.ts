/**
 * 项目模板表单工具单测：解析、method 同步、提交校验。
 * 纯函数，无 UI / 持久化。
 */
import { describe, expect, it } from 'vitest'

import { buildLoginApi, buildTemplateRow } from '@/test/project/helpers/buildTemplateRow'
import {
  buildLoginGraphJson,
  emptyPrefabricatedApi,
  emptyPrefabFlow,
  emptyPrefabPrompt,
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
  validateEnvs,
  validateParams,
  validatePrompts,
  validateTemplateGraphApiBindings,
} from '@/views/project/testProjectTemplate/utils/templateForm'
import { synthesizeTemplateApiCatalog } from '@/views/project/testProjectTemplate/utils/synthesizeTemplateApiTree'

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
  it('默认 POST 且 authConfig.mode=none，并带合成 id', () => {
    // 前提：新建预制接口
    const api = emptyPrefabricatedApi()

    // 期望：登录口常用默认值 + 雪花 id；上传保护默认关
    expect(resolveApiMethod(api)).toBe('POST')
    expect(api.authConfig.mode).toBe('none')
    expect(api.requestConfig.body.mode).toBe('json')
    expect(api.testProjectApiId).toMatch(/^\d+$/)
    expect(api.syncProtected).toBe(0)
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
  it('只保留 asset，丢弃 flow/env 与旧 value/extract/assert', () => {
    // 前提：混有 asset 与其它 kind
    const rows = [
      { kind: 'flow', name: 'token', value: '' },
      { kind: 'env', name: 'timeout', value: '5000' },
      { kind: 'asset', name: 'clientAuth', value: { mobile: '13800000001' } },
      { kind: 'assert', left: 'http.body.code', operator: 'eq', right: '0' },
      { kind: 'value', name: 'username', value: 'admin', bind: { method: 'POST', path: '/login' } },
    ]

    const next = validateParams(rows)

    // 期望：仅 asset
    expect(next).toHaveLength(1)
    expect(next.map((r) => r.kind)).toEqual(['asset'])
  })
})

describe('buildLoginGraphJson', () => {
  it('组装探活再登录图，默认抽到 asset', () => {
    // 前提：组装登录骨架（默认 asset 口径）
    const graph = buildLoginGraphJson({
      method: 'POST',
      apiPath: '/login',
      from: 'body',
      expr: '$.token',
    })

    // 期望：Condition → 探活(whitelist) → Condition → 登录；抽取写 asset.adminAuth.token
    const byId = Object.fromEntries(graph.nodes.map((n) => [n.id, n]))
    expect(byId.cond_token.type).toBe('condition')
    expect(byId.probe_http.data.statusCheck).toEqual({ mode: 'whitelist', values: [200, 401] })
    expect(byId.probe_http.data.successCheck).toEqual({ mode: 'off' })
    expect(byId.probe_http.data.apiPath).toBe('/getInfo')
    expect(byId.login_http.data.apiPath).toBe('/login')
    expect(byId.login_http.data.timeoutMs).toBe(30000)
    expect(byId.login_http.data.successCheck).toEqual({ mode: 'inherit' })
    expect(byId.login_http.data.extracts[0]).toMatchObject({
      scope: 'asset',
      entryKey: 'adminAuth',
      fieldPath: 'token',
      expr: '$.token',
    })
    expect(byId.reuse_end).toBeUndefined()
    const aliveIf = byId.cond_alive.data.branches.find((b) => b.id === 'b_alive_if')
    expect(aliveIf?.target).toBeUndefined()
    expect(aliveIf?.terminal).toBeUndefined()
    expect(aliveIf?.target).toBeUndefined()
    expect(graph.edges.length).toBe(4)
    expect(graph.meta.layout).toBe('manual')
    expect(graph.meta.scenarios).toHaveLength(1)
    expect(graph.meta.flowOutputs).toEqual([{ name: 'adminAuth.token' }])
  })

  it('写入合成 testProjectApiId', () => {
    // 前提：传入登录/探活合成 id
    const graph = buildLoginGraphJson({
      method: 'POST',
      apiPath: '/login',
      from: 'body',
      expr: '$.token',
      loginApiId: '2100000000000004101',
      probeApiId: '2100000000000004104',
      loginApiName: '登录',
      probeApiName: '获取用户信息',
    })

    // 期望：探活/登录节点绑定合成 id
    const byId = Object.fromEntries(graph.nodes.map((n) => [n.id, n]))
    expect(byId.probe_http.data.testProjectApiId).toBe('2100000000000004104')
    expect(byId.login_http.data.testProjectApiId).toBe('2100000000000004101')
    expect(byId.probe_http.data.apiName).toBe('获取用户信息')
  })

  it('emptyPrefabFlow 按 templateApis 自动绑登录/探活', () => {
    const apis = [
      {
        testProjectApiId: '2100000000000004101',
        apiName: '登录',
        apiPath: '/login',
        requestConfig: { method: 'POST' },
      },
      {
        testProjectApiId: '2100000000000004104',
        apiName: '获取用户信息',
        apiPath: '/getInfo',
        requestConfig: { method: 'GET' },
      },
    ]
    const { graphJson, ensuredApis } = emptyPrefabFlow(apis)
    const byId = Object.fromEntries(graphJson.nodes.map((n) => [n.id, n]))
    expect(byId.login_http.data.testProjectApiId).toBe('2100000000000004101')
    expect(byId.probe_http.data.testProjectApiId).toBe('2100000000000004104')
    expect(ensuredApis).toHaveLength(2)
  })
})

describe('validateApis / synthesize', () => {
  it('validateApis 为缺 id 行补合成 id', () => {
    // 前提：无 testProjectApiId 的预制口
    const list = validateApis([{ apiName: '登录', apiPath: '/login', requestConfig: { method: 'POST' } }])

    // 期望：落库前已有稳定 id
    expect(list[0].testProjectApiId).toMatch(/^\d+$/)
  })

  it('合成 catalog 读行上 id，不因下标漂移', () => {
    const { tree, catalog } = synthesizeTemplateApiCatalog([
      { testProjectApiId: '2100000000000004101', apiName: '登录', apiPath: '/login', requestConfig: { method: 'POST' } },
      { testProjectApiId: '2100000000000004104', apiName: '获取用户信息', apiPath: '/getInfo', requestConfig: { method: 'GET' } },
    ])

    expect(catalog.map((c) => c.syntheticId)).toEqual(['2100000000000004101', '2100000000000004104'])
    // 空分组落入「默认分组」
    expect(tree[0].groupName).toBe('默认分组')
    expect(tree[0].children[0].testProjectApiId).toBe('2100000000000004101')
  })
})

describe('validateTemplateGraphApiBindings', () => {
  it('硬拦未绑定或 catalog 外合成 id', () => {
    const catalog = [{ syntheticId: '2100000000000004101', api: {} }]
    const unbound = {
      nodes: [{ id: 'n1', type: 'http', data: { callMode: 'project', name: '登录' } }],
    }
    const foreign = {
      nodes: [{ id: 'n1', type: 'http', data: { callMode: 'project', name: '登录', testProjectApiId: '2100000000000004999' } }],
    }
    const ok = {
      nodes: [{ id: 'n1', type: 'http', data: { callMode: 'project', name: '登录', testProjectApiId: '2100000000000004101' } }],
    }

    expect(validateTemplateGraphApiBindings(unbound, catalog).ok).toBe(false)
    expect(validateTemplateGraphApiBindings(foreign, catalog).ok).toBe(false)
    expect(validateTemplateGraphApiBindings(ok, catalog).ok).toBe(true)
  })
})

describe('emptyTemplateForm', () => {
  it('默认 templateApis/templateParams/templateEnvs/templateFlows/templatePrompts 为空数组', () => {
    // 前提：新增模板
    const form = emptyTemplateForm()

    // 期望：由面板引导用户新增预制口；参数、环境、流、提示词可空
    expect(form.templateApis).toEqual([])
    expect(form.templateParams).toEqual([])
    expect(form.templateEnvs).toEqual([])
    expect(form.templateFlows).toEqual([])
    expect(form.templatePrompts).toEqual([])
  })
})

describe('validatePrompts', () => {
  it('丢掉缺 title/content 的行并补默认 sessionScene', () => {
    const list = validatePrompts([
      { title: 'S01', content: '查车', sortNum: 1 },
      { title: '空正文', content: '  ' },
      emptyPrefabPrompt(),
    ])

    expect(list).toHaveLength(1)
    expect(list[0]).toMatchObject({
      title: 'S01',
      content: '查车',
      sessionScene: 'test_flow_design',
      sortNum: 1,
    })
  })
})

describe('templateToForm / formToPayload', () => {
  it('round-trip 保留 templateApis 数组且不强制 header', () => {
    // 前提：详情含一条登录口
    const row = buildTemplateRow()

    const form = templateToForm(row)
    const payload = formToPayload(form)

    // 期望：提交体 templateApis 仍为合法 JSON 字符串；params/flows/prompts 为数组 JSON
    expect(form.templateApis).toHaveLength(1)
    expect(form.pathPrefixText).toBe('/api/')
    const parsed = JSON.parse(payload.templateApis)
    expect(parsed[0].apiPath).toBe('/login')
    expect(payload.templateName).toBe('测试模板')
    expect(JSON.parse(payload.templateParams)).toEqual([])
    expect(JSON.parse(payload.templateEnvs)).toEqual([])
    expect(JSON.parse(payload.templateFlows)).toEqual([])
    expect(JSON.parse(payload.templatePrompts)).toEqual([])
    expect(payload.headerName).toBeUndefined()
    // 雪花 ID 保持字符串，避免 Number 精度丢失
    expect(payload.testProjectTemplateId).toBe('1')
  })

  it('round-trip 保留预制提示词', () => {
    const row = buildTemplateRow({
      templatePrompts: [
        {
          title: 'S01 购物车',
          description: '场景 S01',
          content: '已挂子流。查车下单。',
          sessionScene: 'test_flow_design',
          sortNum: 501,
          remark: 'S01',
        },
      ],
    })

    const form = templateToForm(row)
    const payload = formToPayload(form)

    expect(form.templatePrompts).toHaveLength(1)
    expect(JSON.parse(payload.templatePrompts)[0].title).toBe('S01 购物车')
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

  it('formToPayload 丢弃 params 中的 kind=env/flow', () => {
    // 前提：表单 params 误带 env/flow 行；环境在 templateEnvs
    const row = buildTemplateRow({
      templateParams: [
        { kind: 'asset', name: 'adminAuth', value: { username: 'admin' } },
        { kind: 'env', name: 'timeout', value: '5000', remark: '毫秒' },
        { kind: 'flow', name: 'debugToken', value: 'x' },
      ],
      templateEnvs: [
        {
          envName: '默认环境',
          envUrl: 'http://localhost:8801',
          envVariables: [{ key: 'timeout', remark: '毫秒', assets: { timeout: '5000' } }],
        },
      ],
    })

    const form = templateToForm(row)
    const payload = formToPayload(form)

    // 期望：params 仅 asset；env 只在 templateEnvs
    const params = JSON.parse(payload.templateParams)
    expect(params.map((r) => r.kind)).toEqual(['asset'])
    expect(JSON.parse(payload.templateEnvs)[0].envVariables).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ key: 'timeout' }),
      ]),
    )
  })

  it('round-trip 保留 templateEnvs 名称与 URL', () => {
    const row = buildTemplateRow({
      templateEnvs: [
        {
          envName: '默认环境',
          envUrl: 'http://localhost:8801',
          envVariables: [{ key: 'region', remark: '', assets: { region: 'cn' } }],
        },
      ],
    })

    const form = templateToForm(row)
    const payload = formToPayload(form)

    expect(form.templateEnvs[0].envUrl).toBe('http://localhost:8801')
    expect(JSON.parse(payload.templateEnvs)[0]).toMatchObject({
      envName: '默认环境',
      envUrl: 'http://localhost:8801',
    })
    expect(JSON.parse(payload.templateEnvs)[0].envVariables[0].key).toBe('region')
  })

  it('validateEnvs 丢掉全空行', () => {
    expect(validateEnvs([{ envName: '', envUrl: '', envVariables: [] }])).toEqual([])
  })

  it('pathPrefix 非法时 formToPayload 失败', () => {
    // 前提：apis 合法但 pathPrefix 为 /
    const form = templateToForm(buildTemplateRow())
    form.pathPrefixText = '/'

    // 期望：提交前抛错
    expect(() => formToPayload(form)).toThrow('pathPrefix 禁止使用 "/"')
  })
})
