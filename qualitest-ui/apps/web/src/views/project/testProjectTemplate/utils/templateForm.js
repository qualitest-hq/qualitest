/**
 * 项目模板表单工具：路径匹配、预制接口 / 参数(flow·env·asset) / 测试流的解析、校验与提交组装。
 * 托管请求头不在模板表单提交；勾选进项目时由后端按预制测试流抽取规则生成。
 */

import { formatAuthModeLabel, parseJsonMaybe, splitPathLines } from '../../testProject/utils/projectAuthConfig'
import { ensureTemplateApiIds, newTemplateApiId } from './templateApiId'
import { mergeFlowSeedFromTemplateParams, partitionTemplateParams } from './templateParamUtils'
import { LOGIN_FLOW_NODE_LAYOUT, LOGIN_FLOW_EDGES, hydrateTemplateFlowsGraphs } from './templateCanvasHydrate'
import { synthesizeTemplateApiCatalog } from './synthesizeTemplateApiTree'

export { mergeFlowSeedFromTemplateParams, partitionTemplateParams }

/** 模板编辑里可选的 HTTP 方法。 */
const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

/** 深拷贝 JSON 可序列化对象，避免表单与提交互相污染。 */
function cloneJson(value) {
  return JSON.parse(JSON.stringify(value))
}

/** 从 matchConfig 取出 pathPrefix 字符串列表。 */
function listPathPrefixes(matchConfig) {
  const obj = parseJsonMaybe(matchConfig)
  const list = Array.isArray(obj?.pathPrefix) ? obj.pathPrefix : []
  return list.map((s) => String(s).trim()).filter(Boolean)
}

/** matchConfig JSON → 多行 pathPrefix 文本（编辑框用）。 */
export function matchConfigToPathPrefixText(matchConfig) {
  return listPathPrefixes(matchConfig).join('\n')
}

/**
 * 多行 pathPrefix 文本 → matchConfig JSON 字符串。
 * 空内容返回 null；单独写「/」禁止。
 */
export function pathPrefixTextToMatchConfig(text) {
  const prefixes = splitPathLines(text)
  if (!prefixes.length) return null
  if (prefixes.some((x) => x === '/')) {
    throw new Error('pathPrefix 禁止使用 "/"')
  }
  return JSON.stringify({ pathPrefix: prefixes })
}

/**
 * 新建一条预制接口的默认结构。
 * 默认 POST + authConfig.mode=none（登录口免登常见默认）。
 */
export function emptyPrefabricatedApi() {
  return {
    testProjectApiId: newTemplateApiId(),
    apiName: '',
    apiPath: '',
    apiGroup: '',
    protocolType: 'http',
    apiStatus: 'normal',
    requestConfig: {
      method: 'POST',
      configVersion: 1,
      body: { mode: 'json', json: { example: {} } },
      pathParams: [],
      queryParams: [],
      declaredHeaders: [],
    },
    headers: {},
    cookies: {},
    responseConfig: {
      configVersion: 1,
      responses: [],
    },
    testValueConfig: {},
    bizCodeConfig: {},
    authConfig: { mode: 'none' },
    designHints: { hints: [] },
    preRequestScript: null,
    postRequestScript: null,
  }
}

/**
 * 新建一条预制参数：flow / env / asset 之一。
 * @param {'flow'|'env'|'asset'} kind
 */
export function emptyPrefabParam(kind = 'flow') {
  if (kind === 'env') {
    return { kind: 'env', name: '', value: '', remark: '' }
  }
  if (kind === 'asset') {
    return { kind: 'asset', name: '', value: '', remark: '' }
  }
  return { kind: 'flow', name: '', value: '', remark: '' }
}

/** 按 method + apiPath 在 templateApis 中查找预制口。 */
export function findTemplateApiByMethodPath(templateApis, method, apiPath) {
  const wantMethod = String(method || 'GET').trim().toUpperCase()
  const wantPath = String(apiPath || '').trim()
  if (!wantPath) return null
  return parseApis(templateApis).find(
    (api) => resolveApiMethod(api) === wantMethod && String(api?.apiPath || '').trim() === wantPath,
  ) || null
}

/** 新建一条预制测试流默认行；有 templateApis 时自动绑登录/探活合成 id。 */
export function emptyPrefabFlow(templateApis = [], overrides = {}) {
  const { apis } = ensureTemplateApiIds(parseApis(templateApis))
  const loginCandidates = [
    overrides.apiPath,
    '/login',
    '/api/account/auth/login',
  ].filter(Boolean)
  const probeCandidates = [
    overrides.probePath,
    '/getInfo',
    '/api/account/auth/profile',
  ].filter(Boolean)
  let loginApi = null
  for (const path of loginCandidates) {
    loginApi = findTemplateApiByMethodPath(apis, 'POST', path)
    if (loginApi) break
  }
  let probeApi = null
  for (const path of probeCandidates) {
    probeApi = findTemplateApiByMethodPath(apis, 'GET', path)
    if (probeApi) break
  }
  const loginPath = loginApi?.apiPath || loginCandidates[0] || '/login'
  const probePath = probeApi?.apiPath || probeCandidates[0] || '/getInfo'
  return {
    flowName: '',
    description: '',
    ensuredApis: apis,
    graphJson: buildLoginGraphJson({
      method: loginApi ? resolveApiMethod(loginApi) : 'POST',
      apiPath: loginPath,
      from: 'body',
      expr: '$.token',
      scope: 'asset',
      entryKey: 'adminAuth',
      fieldPath: 'token',
      probeMethod: 'GET',
      probePath,
      loginApiId: loginApi?.testProjectApiId,
      probeApiId: probeApi?.testProjectApiId,
      loginApiName: loginApi?.apiName,
      probeApiName: probeApi?.apiName,
      ...overrides,
    }),
  }
}

/**
 * 组装「探活再登录」画布 graphJson。
 * Condition(凭证 exists) → 探活(statusCheck whitelist) → Condition(http.status=200) 否则登录。
 * 默认把 token 抽到 asset.adminAuth.token；也可显式传 flowKey 写 flow 变量。
 */
export function buildLoginGraphJson({
  method,
  apiPath,
  extracts,
  from,
  expr,
  scope,
  entryKey,
  fieldPath,
  flowKey,
  timeoutMs = 30000,
  successCheckMode = 'inherit',
  nodeName = '登录',
  probeMethod = 'GET',
  probePath = '/getInfo',
  /** 预制登录 API 合成 id */
  loginApiId,
  /** 预制探活 API 合成 id */
  probeApiId,
  loginApiName,
  probeApiName,
}) {
  const extractScope = String(scope || (flowKey ? 'flow' : 'asset')).trim().toLowerCase() || 'asset'
  const assetEntry = String(entryKey || 'adminAuth').trim() || 'adminAuth'
  const assetField = String(fieldPath || 'token').trim() || 'token'
  const key = String(flowKey || 'token').trim() || 'token'
  const assetLeft = `asset.${assetEntry}.${assetField}`
  const safeProbeMethod = String(probeMethod || 'GET').trim().toUpperCase() || 'GET'
  const safeProbePath = String(probePath || '/getInfo').trim() || '/getInfo'
  let extractList = Array.isArray(extracts)
    ? extracts.filter((e) => e && (e.expr || e.name || e.entryKey))
    : null
  if (!extractList || !extractList.length) {
    if (extractScope === 'flow') {
      extractList = [
        {
          from: from || 'body',
          expr: String(expr || '').trim(),
          scope: 'flow',
          name: key,
          entryKey: '',
          fieldPath: '',
        },
      ]
    } else {
      extractList = [
        {
          from: from || 'body',
          expr: String(expr || '').trim() || '$.token',
          scope: 'asset',
          name: assetField,
          entryKey: assetEntry,
          fieldPath: assetField,
        },
      ]
    }
  } else {
    extractList = extractList.map((e) => ({
      from: e.from || 'body',
      expr: String(e.expr || '').trim(),
      scope: e.scope || 'flow',
      name: String(e.name || '').trim(),
      entryKey: e.entryKey != null ? String(e.entryKey) : '',
      fieldPath: e.fieldPath != null ? String(e.fieldPath) : '',
    }))
  }
  const flowOutputs =
    extractScope === 'asset'
      ? [{ name: `${assetEntry}.${assetField}` }]
      : extractList.filter((e) => (e.scope || 'flow') === 'flow' && e.name).map((e) => ({ name: e.name }))
  const scenarioId = `sc_${Date.now()}`
  const timeout = Number(timeoutMs) > 0 ? Number(timeoutMs) : 30000
  return {
    nodes: [
      {
        id: 'cond_token',
        type: 'condition',
        position: { ...LOGIN_FLOW_NODE_LAYOUT.cond_token },
        data: {
          name: '凭证是否存在',
          summary: '凭证是否存在',
          branches: [
            {
              id: 'b_token_if',
              kind: 'if',
              target: 'probe_http',
              conditions: [{ left: assetLeft, operator: 'exists', right: '' }],
            },
            { id: 'b_token_else', kind: 'else', target: 'login_http', conditions: [] },
          ],
        },
      },
      {
        id: 'probe_http',
        type: 'http',
        position: { ...LOGIN_FLOW_NODE_LAYOUT.probe_http },
        data: {
          name: '探活',
          callMode: 'project',
          httpMethod: safeProbeMethod,
          apiPath: safeProbePath,
          ...(String(probeApiId || '').trim()
            ? {
                testProjectApiId: String(probeApiId).trim(),
                apiName: String(probeApiName || '').trim() || '探活',
              }
            : {}),
          timeoutMs: timeout,
          statusCheck: { mode: 'whitelist', values: [200, 401] },
          successCheck: { mode: 'off' },
          extracts: [],
          summary: String(probeApiId || '').trim()
            ? `${safeProbeMethod} ${String(probeApiName || '').trim() || safeProbePath}`
            : '探活',
        },
      },
      {
        id: 'cond_alive',
        type: 'condition',
        position: { ...LOGIN_FLOW_NODE_LAYOUT.cond_alive },
        data: {
          name: '凭证是否有效',
          summary: '凭证是否有效',
          branches: [
            {
              id: 'b_alive_if',
              kind: 'if',
              terminal: true,
              conditions: [{ left: 'http.status', operator: 'eq', right: '200' }],
            },
            { id: 'b_alive_else', kind: 'else', target: 'login_http', conditions: [] },
          ],
        },
      },
      {
        id: 'login_http',
        type: 'http',
        position: { ...LOGIN_FLOW_NODE_LAYOUT.login_http },
        data: {
          name: nodeName || '登录',
          callMode: 'project',
          httpMethod: String(method || 'POST').trim().toUpperCase() || 'POST',
          apiPath: String(apiPath || '').trim(),
          ...(String(loginApiId || '').trim()
            ? {
                testProjectApiId: String(loginApiId).trim(),
                apiName: String(loginApiName || '').trim() || nodeName || '登录',
              }
            : {}),
          timeoutMs: timeout,
          successCheck: { mode: successCheckMode === 'off' ? 'off' : 'inherit' },
          extracts: extractList,
          summary: String(loginApiId || '').trim()
            ? `${String(method || 'POST').trim().toUpperCase() || 'POST'} ${String(loginApiName || '').trim() || String(apiPath || '').trim() || nodeName || '登录'}`
            : nodeName || '登录',
        },
      },
    ],
    edges: LOGIN_FLOW_EDGES.map((e) => ({ ...e })),
    meta: {
      schemaVersion: 1,
      layout: 'manual',
      viewport: { x: 0, y: 0, zoom: 0.85 },
      flowOutputs: flowOutputs.length ? flowOutputs : [],
      activeScenarioId: scenarioId,
      scenarios: [
        {
          id: scenarioId,
          name: '默认（冒烟）',
          testProjectEnvId: '',
          flowSeed: {},
          remark: '',
        },
      ],
    },
  }
}

/**
 * 把 JSON 字符串或数组解析成对象数组。
 * 非数组 / 非法 JSON 返回 []；非对象元素落成 {}。
 */
export function parseJsonObjectArray(raw) {
  if (Array.isArray(raw)) {
    return raw.map((item) => (item && typeof item === 'object' ? { ...item } : {}))
  }
  const parsed = parseJsonMaybe(raw)
  if (!Array.isArray(parsed)) return []
  return parsed.map((item) => (item && typeof item === 'object' ? { ...item } : {}))
}

/** 解析预制接口字段。 */
export function parseApis(apis) {
  return parseJsonObjectArray(apis)
}

/** 解析预制参数字段。 */
export function parseParams(params) {
  return parseJsonObjectArray(params)
}

/** 解析预制测试流字段。 */
export function parseFlows(flows) {
  return parseJsonObjectArray(flows)
}

/** 校验预制接口：必须非空数组；补齐稳定合成 id；返回深拷贝。 */
export function validateApis(apis) {
  const list = parseApis(apis)
  if (!list.length) {
    throw new Error('预制接口须为非空数组')
  }
  const { apis: withIds } = ensureTemplateApiIds(list.map((api) => cloneJson(api)))
  return withIds
}

/** 校验预制参数：只保留 kind=flow|env|asset 的合法行。 */
export function validateParams(params) {
  return parseParams(params)
    .map((row) => cloneJson(row))
    .filter((row) => {
      const kind = String(row?.kind || '').trim()
      return (kind === 'flow' || kind === 'env' || kind === 'asset') && String(row?.name || '').trim()
    })
    .map((row) => {
      const kind = String(row.kind).trim()
      let value = row.value != null ? row.value : ''
      if (kind === 'asset' && typeof value === 'string') {
        const text = value.trim()
        if ((text.startsWith('{') && text.endsWith('}')) || (text.startsWith('[') && text.endsWith(']'))) {
          try {
            value = JSON.parse(text)
          } catch {
            /* 保持原串 */
          }
        }
      }
      return {
        kind,
        name: String(row.name).trim(),
        value,
        remark: row.remark != null ? String(row.remark) : '',
      }
    })
}

/** 校验预制测试流：可空；丢掉没有 flowName 的行；返回深拷贝。 */
export function validateFlows(flows) {
  return parseFlows(flows)
    .filter((row) => String(row?.flowName || '').trim())
    .map((row) => cloneJson(row))
}

/** 从 requestConfig 读取 HTTP 方法；非法则 GET。 */
export function resolveApiMethod(api) {
  const cfg = parseJsonMaybe(api?.requestConfig)
  const method = String(cfg?.method || 'GET').trim().toUpperCase()
  return HTTP_METHODS.includes(method) ? method : 'GET'
}

/** 写入 requestConfig.method，返回新对象。 */
export function setApiMethod(api, method) {
  const nextMethod = String(method || 'GET').trim().toUpperCase()
  const cfg = parseJsonMaybe(api?.requestConfig) || {}
  return {
    ...api,
    requestConfig: {
      ...cfg,
      method: HTTP_METHODS.includes(nextMethod) ? nextMethod : 'GET',
    },
  }
}

/** requestConfig 编辑后若含合法 method，同步回接口对象。 */
export function syncMethodFromRequestConfig(api, requestConfig) {
  const cfg = parseJsonMaybe(requestConfig)
  if (!cfg || typeof cfg !== 'object') return api
  const method = String(cfg.method || '').trim().toUpperCase()
  if (!method || !HTTP_METHODS.includes(method)) return { ...api, requestConfig: cfg }
  return { ...api, requestConfig: { ...cfg, method } }
}

/** 用整条对象替换预制接口数组中指定下标。 */
export function replaceApiAtIndex(apis, index, nextApi) {
  const list = parseApis(apis)
  if (index < 0 || index >= list.length) {
    throw new Error('接口索引无效')
  }
  if (!nextApi || typeof nextApi !== 'object' || Array.isArray(nextApi)) {
    throw new Error('预制接口须为 JSON 对象')
  }
  const cloned = validateApis(list)
  cloned[index] = cloneJson(nextApi)
  return cloned
}

/** 预制接口 → 列表预览行（方法、路径、名称、鉴权模式标签）。 */
export function apisToPreviewRows(apis) {
  const list = parseApis(apis)
  return list.map((api) => ({
    method: resolveApiMethod(api),
    apiPath: String(api?.apiPath || '').trim(),
    apiName: String(api?.apiName || '').trim(),
    authMode: String(api?.authConfig?.mode || 'inherit').trim() || 'inherit',
    authModeLabel: formatAuthModeLabel(api?.authConfig?.mode),
  }))
}

/** 模板列表：pathPrefix 摘要；过长截断。 */
export function formatPathPrefixSummary(matchConfig) {
  const list = listPathPrefixes(matchConfig)
  if (!list.length) return '—'
  const text = list.join('、')
  return text.length > 48 ? text.slice(0, 48) + '…' : text
}

/** 勾选列表副标题：有 pathPrefix 时显示「匹配 xxx」。 */
export function formatPathPrefixHint(matchConfig) {
  const list = listPathPrefixes(matchConfig)
  if (!list.length) return ''
  return '匹配 ' + list.join('、')
}

/** 预制测试流摘要：流名 + 首个 HTTP 节点的方法 / 路径 / 抽取标签。 */
export function summarizePrefabFlow(flow) {
  const graph = parseJsonMaybe(flow?.graphJson) || flow?.graphJson || {}
  const node = Array.isArray(graph?.nodes) ? graph.nodes.find((n) => n?.type === 'http') : null
  const data = node?.data || {}
  const extract = Array.isArray(data.extracts) && data.extracts[0] ? data.extracts[0] : null
  let extractTarget = ''
  if (extract) {
    const scope = String(extract.scope || 'flow').trim().toLowerCase()
    if (scope === 'asset' && extract.entryKey) {
      const field = extract.fieldPath || extract.name || ''
      extractTarget = field ? `asset.${extract.entryKey}.${field}` : `asset.${extract.entryKey}`
    } else if (extract.name) {
      extractTarget = `${scope || 'flow'}.${extract.name}`
    }
  }
  return {
    flowName: String(flow?.flowName || '').trim(),
    method: String(data.httpMethod || 'POST').trim().toUpperCase(),
    apiPath: String(data.apiPath || '').trim(),
    extractLabel: extract
      ? `${extract.expr || ''} → ${extractTarget || '—'}`
      : '—',
  }
}

/** 空模板表单（新增抽屉初始值）。 */
export function emptyTemplateForm() {
  return {
    testProjectTemplateId: undefined,
    templateName: '',
    pathPrefixText: '',
    templateApis: [],
    templateParams: [],
    templateFlows: [],
    enableStatus: 1,
    sortNum: 0,
    remark: '',
    builtinStatus: 0,
  }
}

/** 详情接口行 → 编辑表单（JSON 字段解析成数组；缺 id 的预制口补合成 id）。 */
export function templateToForm(row) {
  const { apis } = ensureTemplateApiIds(parseApis(row?.templateApis))
  const { catalog } = synthesizeTemplateApiCatalog(apis)
  const { flows: hydratedFlows } = hydrateTemplateFlowsGraphs(parseFlows(row?.templateFlows), catalog)
  return {
    testProjectTemplateId: row?.testProjectTemplateId,
    templateName: row?.templateName || '',
    pathPrefixText: matchConfigToPathPrefixText(row?.matchConfig),
    templateApis: apis,
    templateParams: parseParams(row?.templateParams),
    templateFlows: hydratedFlows,
    enableStatus: row?.enableStatus ?? 1,
    sortNum: row?.sortNum ?? 0,
    remark: row?.remark || '',
    builtinStatus: row?.builtinStatus ?? 0,
  }
}

/**
 * 模板画布保存：project HTTP 节点的合成 id 必须落在当前 templateApis catalog。
 * @returns {{ ok: true } | { ok: false, message: string }}
 */
export function validateTemplateGraphApiBindings(graph, catalog) {
  const allowed = new Set(
    (Array.isArray(catalog) ? catalog : [])
      .map((e) => String(e?.syntheticId ?? '').trim())
      .filter(Boolean),
  )
  const nodes = Array.isArray(graph?.nodes) ? graph.nodes : []
  for (const node of nodes) {
    if (!node || String(node.type || '').toLowerCase() !== 'http') continue
    const data = node.data || {}
    if (String(data.callMode || 'project').toLowerCase() !== 'project') continue
    const id = String(data.testProjectApiId ?? '').trim()
    const label = String(data.name || node.id || 'HTTP').trim()
    if (!id) {
      return { ok: false, message: `节点「${label}」未绑定预制接口` }
    }
    if (!allowed.has(id)) {
      return { ok: false, message: `节点「${label}」绑定的接口不在当前预制接口列表中` }
    }
  }
  return { ok: true }
}

/**
 * 编辑表单 → 提交体。
 * 写出 templateApis / templateParams / templateFlows 的 JSON 字符串；
 * 不提交托管头字段；雪花 id 保持字符串。
 */
export function formToPayload(form) {
  const matchConfig = pathPrefixTextToMatchConfig(form.pathPrefixText)
  const templateApis = JSON.stringify(validateApis(form.templateApis))
  const templateParams = JSON.stringify(validateParams(form.templateParams))
  const templateFlows = JSON.stringify(validateFlows(form.templateFlows))
  const payload = {
    templateName: String(form.templateName || '').trim(),
    templateApis,
    templateParams,
    templateFlows,
    enableStatus: form.enableStatus ?? 1,
    sortNum: form.sortNum ?? 0,
    remark: form.remark || '',
  }
  if (matchConfig != null) {
    payload.matchConfig = matchConfig
  }
  if (form.testProjectTemplateId != null && form.testProjectTemplateId !== '') {
    payload.testProjectTemplateId = String(form.testProjectTemplateId)
  }
  return payload
}

export { HTTP_METHODS, parseJsonMaybe }
