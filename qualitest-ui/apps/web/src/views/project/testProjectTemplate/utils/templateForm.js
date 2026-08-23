/**
 * 项目模板表单：matchConfig / apis 与 pathPrefix 文本互转。
 */

import { formatAuthModeLabel, parseJsonMaybe, splitPathLines } from '../../testProject/utils/projectAuthConfig'

const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

function listPathPrefixes(matchConfig) {
  const obj = parseJsonMaybe(matchConfig)
  const list = Array.isArray(obj?.pathPrefix) ? obj.pathPrefix : []
  return list.map((s) => String(s).trim()).filter(Boolean)
}

/** matchConfig JSON → pathPrefix 多行文本 */
export function matchConfigToPathPrefixText(matchConfig) {
  return listPathPrefixes(matchConfig).join('\n')
}

/** pathPrefix 多行文本 → matchConfig JSON 字符串；空则 null */
export function pathPrefixTextToMatchConfig(text) {
  const prefixes = splitPathLines(text)
  if (!prefixes.length) return null
  if (prefixes.some((x) => x === '/')) {
    throw new Error('pathPrefix 禁止使用 "/"')
  }
  return JSON.stringify({ pathPrefix: prefixes })
}

/** 新建预制接口默认结构 */
export function emptyPrefabricatedApi() {
  return {
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

/** 解析 apis（字符串或数组）为对象数组 */
export function parseApis(apis) {
  if (Array.isArray(apis)) {
    return apis.map((item) => (item && typeof item === 'object' ? { ...item } : {}))
  }
  const parsed = parseJsonMaybe(apis)
  if (!Array.isArray(parsed)) return []
  return parsed.map((item) => (item && typeof item === 'object' ? { ...item } : {}))
}

/** 校验 apis 数组；通过返回深拷贝数组，失败抛错 */
export function validateApis(apis) {
  const list = parseApis(apis)
  if (!list.length) {
    throw new Error('预制接口须为非空数组')
  }
  return list.map((api) => JSON.parse(JSON.stringify(api)))
}

/** 从 requestConfig 读取 HTTP 方法 */
export function resolveApiMethod(api) {
  const cfg = parseJsonMaybe(api?.requestConfig)
  const method = String(cfg?.method || 'GET').trim().toUpperCase()
  return HTTP_METHODS.includes(method) ? method : 'GET'
}

/** 写入 requestConfig.method */
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

/** requestConfig JSON 变更后若含 method 则同步到 api */
export function syncMethodFromRequestConfig(api, requestConfig) {
  const cfg = parseJsonMaybe(requestConfig)
  if (!cfg || typeof cfg !== 'object') return api
  const method = String(cfg.method || '').trim().toUpperCase()
  if (!method || !HTTP_METHODS.includes(method)) return { ...api, requestConfig: cfg }
  return { ...api, requestConfig: { ...cfg, method } }
}

/** 用整条对象替换 apis 中指定下标 */
export function replaceApiAtIndex(apis, index, nextApi) {
  const list = parseApis(apis)
  if (index < 0 || index >= list.length) {
    throw new Error('接口索引无效')
  }
  if (!nextApi || typeof nextApi !== 'object' || Array.isArray(nextApi)) {
    throw new Error('预制接口须为 JSON 对象')
  }
  const cloned = validateApis(list)
  cloned[index] = JSON.parse(JSON.stringify(nextApi))
  return cloned
}

/** apis → 预览表格行 */
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

/** 列表展示：pathPrefix 摘要 */
export function formatPathPrefixSummary(matchConfig) {
  const list = listPathPrefixes(matchConfig)
  if (!list.length) return '—'
  const text = list.join('、')
  return text.length > 48 ? text.slice(0, 48) + '…' : text
}

/** 勾选列表副标题：pathPrefix 提示 */
export function formatPathPrefixHint(matchConfig) {
  const list = listPathPrefixes(matchConfig)
  if (!list.length) return ''
  return '匹配 ' + list.join('、')
}

export function emptyTemplateForm() {
  return {
    testProjectTemplateId: undefined,
    templateName: '',
    headerName: 'Authorization',
    headerValueTemplate: 'Bearer {{flow.token}}',
    pathPrefixText: '',
    apis: [],
    enableStatus: 1,
    sortNum: 0,
    remark: '',
    builtinStatus: 0,
  }
}

/** 详情 → 表单 */
export function templateToForm(row) {
  return {
    testProjectTemplateId: row?.testProjectTemplateId,
    templateName: row?.templateName || '',
    headerName: row?.headerName || 'Authorization',
    headerValueTemplate: row?.headerValueTemplate || '',
    pathPrefixText: matchConfigToPathPrefixText(row?.matchConfig),
    apis: parseApis(row?.apis),
    enableStatus: row?.enableStatus ?? 1,
    sortNum: row?.sortNum ?? 0,
    remark: row?.remark || '',
    builtinStatus: row?.builtinStatus ?? 0,
  }
}

/** 表单 → 提交体 */
export function formToPayload(form) {
  const matchConfig = pathPrefixTextToMatchConfig(form.pathPrefixText)
  const apis = JSON.stringify(validateApis(form.apis))
  const payload = {
    templateName: String(form.templateName || '').trim(),
    headerName: String(form.headerName || '').trim(),
    headerValueTemplate: String(form.headerValueTemplate || '').trim(),
    apis,
    enableStatus: form.enableStatus ?? 1,
    sortNum: form.sortNum ?? 0,
    remark: form.remark || '',
  }
  if (matchConfig != null) {
    payload.matchConfig = matchConfig
  }
  if (form.testProjectTemplateId != null && form.testProjectTemplateId !== '') {
    payload.testProjectTemplateId = Number(form.testProjectTemplateId)
  }
  return payload
}

export { HTTP_METHODS }
