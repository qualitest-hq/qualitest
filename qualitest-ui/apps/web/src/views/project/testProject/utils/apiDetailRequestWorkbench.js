/**
 * API 详情页请求工作台工具。
 * <p>
 * 负责从 TestProjectApi 详情解析/组装调试与文档共用的草稿状态：
 * requestConfig（configVersion=1、queryParams、pathParams、declaredHeaders、body）、
 * 调试 headers/cookies（object 键值转 KV 行）、参数行规范化与 body 结构补齐。
 */

import { REQUEST_CONFIG_VERSION } from './apiConfigV2Constants'

export { REQUEST_CONFIG_VERSION } from './apiConfigV2Constants'

const DISALLOWED_URLENC_TYPES = ['file', 'object', 'array', 'any', 'null']

const DISALLOWED_QUERY_PATH_TYPES = ['object', 'array', 'any', 'null']

/** 校正 urlencoded 参数行的 type，禁止 file/object/array 等 */
export function coerceUrlencodedRowTypes(rows) {
  if (!Array.isArray(rows)) return
  for (const r of rows) {
    const t = String(r.type || '').toLowerCase()
    if (DISALLOWED_URLENC_TYPES.includes(t)) r.type = 'string'
  }
}

/** 校正 query/path 参数行的 type，仅允许 URL 友好标量 */
export function coerceQueryPathRowTypes(rows) {
  if (!Array.isArray(rows)) return
  for (const r of rows) {
    const t = String(r.type || '').toLowerCase()
    if (DISALLOWED_QUERY_PATH_TYPES.includes(t)) r.type = 'string'
  }
}

function parseOptionalNumber(v) {
  if (v === '' || v == null) return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

/** 名称与值均为空视为「空行」（用于默认保留一行、末尾自动追加一行） */
function isRowContentEmpty(row) {
  if (!row || typeof row !== 'object') return true
  if (row._file) return false
  const n = String(row.name ?? '').trim()
  const v = String(row.value ?? '').trim()
  return !n && !v
}

/** 至少保留一行；末行有内容时追加空行；连续空行合并为末尾单一空行 */
export function ensureTrailingEmptyRow(arr) {
  if (!Array.isArray(arr)) return
  if (!arr.length) {
    arr.push(emptyKVRow())
    return
  }
  while (arr.length > 1) {
    const last = arr[arr.length - 1]
    const prev = arr[arr.length - 2]
    if (isRowContentEmpty(last) && isRowContentEmpty(prev)) {
      arr.pop()
    } else {
      break
    }
  }
  const last = arr[arr.length - 1]
  if (!isRowContentEmpty(last)) {
    arr.push(emptyKVRow())
  }
}

/** 返回空白 KV 参数行模板（含类型、约束等编辑字段默认值） */
export function emptyKVRow() {
  return {
    _enabled: true,
    name: '',
    value: '',
    type: 'string',
    description: '',
    required: false,
    nullable: true,
    deprecated: false,
    format: '',
    behavior: 'readWrite',
    minLength: null,
    maxLength: null,
    minValue: null,
    maxValue: null,
    minItems: null,
    maxItems: null,
    defaultValue: '',
    pattern: ''
  }
}

/**
 * 补齐 body 各模式字段：json/formData/urlencoded/text/binary 等，并规范化子数组。
 */
export function ensureBodyShape(body) {
  const b = body && typeof body === 'object' ? {...body} : {}
  if (!b.mode) b.mode = 'none'
  if (!b.json || typeof b.json !== 'object') {
    b.json = {schema: null, example: null}
  } else {
    b.json = {
      schema: b.json.schema ?? null,
      example: b.json.example !== undefined ? b.json.example : null
    }
  }
  if (!Array.isArray(b.formData)) b.formData = []
  if (!Array.isArray(b.urlencoded)) b.urlencoded = []
  b.formData = b.formData.map(normalizeParamRow)
  b.urlencoded = b.urlencoded.map((r) => {
    const row = normalizeParamRow(r)
    if (String(row.type || '').toLowerCase() === 'file') {
      return {...row, type: 'string'}
    }
    return row
  })
  if (!b.formData.length) b.formData = [emptyKVRow()]
  else ensureTrailingEmptyRow(b.formData)
  if (!b.urlencoded.length) b.urlencoded = [emptyKVRow()]
  else ensureTrailingEmptyRow(b.urlencoded)
  if (b.text == null) b.text = ''
  if (!b.binary || typeof b.binary !== 'object') {
    b.binary = {type: 'file', description: ''}
  } else {
    b.binary = {
      type: b.binary.type || 'file',
      description: b.binary.description != null ? String(b.binary.description) : ''
    }
  }
  return b
}

/** 将单条参数对象规范为工作台 KV 行结构 */
export function normalizeParamRow(p) {
  if (!p || typeof p !== 'object') return emptyKVRow()
  const d = emptyKVRow()
  const {schemaJson: _omitSchemaJson, ...pRest} = p
  const behavior =
      p.behavior === 'readOnly' || p.behavior === 'writeOnly' ? p.behavior : 'readWrite'
  const valueStr = p.value != null ? String(p.value) : ''
  return {
    ...d,
    ...pRest,
    _enabled: p._enabled !== false,
    name: p.name ?? '',
    value: valueStr,
    type: p.type ?? 'string',
    description: p.description ?? '',
    required: !!p.required,
    nullable: p.nullable !== false,
    deprecated: !!p.deprecated,
    format: p.format != null ? String(p.format) : '',
    behavior,
    minLength: parseOptionalNumber(p.minLength),
    maxLength: parseOptionalNumber(p.maxLength),
    minValue: parseOptionalNumber(p.minValue),
    maxValue: parseOptionalNumber(p.maxValue),
    minItems: parseOptionalNumber(p.minItems),
    maxItems: parseOptionalNumber(p.maxItems),
    defaultValue: p.defaultValue != null ? String(p.defaultValue) : '',
    pattern: p.pattern != null ? String(p.pattern) : ''
  }
}

/** 解析字符串或对象形式的 JSON；失败返回 null */
export function parseFlexibleJson(raw) {
  if (raw == null || raw === '') return null
  if (typeof raw === 'object') return JSON.parse(JSON.stringify(raw))
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

/**
 * 将表字段 headers/cookies 的 object JSON 转为 KV 行数组。
 * 非 object（含数组）时返回单行空模板。
 */
export function headersCookiesToRows(raw) {
  const o = parseFlexibleJson(raw)
  if (!o || typeof o !== 'object' || Array.isArray(o)) return [emptyKVRow()]
  const keys = Object.keys(o)
  if (!keys.length) return [emptyKVRow()]
  return keys.map((k) => ({
    ...emptyKVRow(),
    name: k,
    value: String(o[k] ?? '')
  }))
}

/** 空 requestConfig 草稿：GET、空参数行、declaredHeaders=[]、body.mode=none */
export function defaultDraftRequestConfig() {
  return {
    configVersion: REQUEST_CONFIG_VERSION,
    method: 'GET',
    queryParams: [emptyKVRow()],
    pathParams: [emptyKVRow()],
    declaredHeaders: [],
    body: ensureBodyShape(null)
  }
}

/** 从 requestConfig 对象或 JSON 字符串读取 method 并转大写 */
export function extractHttpMethod(requestConfig) {
  if (requestConfig == null || requestConfig === '') return ''
  if (typeof requestConfig === 'object') {
    const raw = requestConfig.method
    if (raw) return String(raw).toUpperCase().trim()
  }
  if (typeof requestConfig === 'string') {
    try {
      const o = JSON.parse(requestConfig)
      if (o?.method) {
        return String(o.method).toUpperCase().trim()
      }
    } catch {
      return ''
    }
  }
  return ''
}

/**
 * @param {object|null|undefined} detail TestProjectApi 详情
 * @returns {{
 *   draftApiPath: string,
 *   draftRequestConfig: object,
 *   draftHeaderRows: object[],
 *   draftCookieRows: object[],
 *   draftPreRequestScript: string,
 *   draftPostRequestScript: string
 * }}
 */
export function buildRequestWorkbenchStateFromDetail(detail) {
  if (!detail) {
    return {
      draftApiPath: '',
      draftRequestConfig: defaultDraftRequestConfig(),
      draftHeaderRows: [emptyKVRow()],
      draftCookieRows: [emptyKVRow()],
      draftPreRequestScript: '',
      draftPostRequestScript: ''
    }
  }

  const draftApiPath = detail.apiPath || ''
  const rc = parseFlexibleJson(detail.requestConfig)
  const methodRaw = rc?.method ?? extractHttpMethod(detail.requestConfig) ?? 'GET'
  const draftRequestConfig = {
    configVersion: REQUEST_CONFIG_VERSION,
    method: String(methodRaw).toUpperCase().trim() || 'GET',
    queryParams: Array.isArray(rc?.queryParams) ? rc.queryParams.map(normalizeParamRow) : [],
    pathParams: Array.isArray(rc?.pathParams) ? rc.pathParams.map(normalizeParamRow) : [],
    declaredHeaders: Array.isArray(rc?.declaredHeaders) ? rc.declaredHeaders.map(normalizeParamRow) : [],
    body: ensureBodyShape(rc?.body)
  }
  if (!draftRequestConfig.queryParams.length) draftRequestConfig.queryParams = [emptyKVRow()]
  else ensureTrailingEmptyRow(draftRequestConfig.queryParams)
  if (!draftRequestConfig.pathParams.length) draftRequestConfig.pathParams = [emptyKVRow()]
  else ensureTrailingEmptyRow(draftRequestConfig.pathParams)

  coerceQueryPathRowTypes(draftRequestConfig.queryParams)
  coerceQueryPathRowTypes(draftRequestConfig.pathParams)
  coerceUrlencodedRowTypes(draftRequestConfig.body.urlencoded)

  const draftHeaderRows = headersCookiesToRows(detail.headers)
  const draftCookieRows = headersCookiesToRows(detail.cookies)
  ensureTrailingEmptyRow(draftHeaderRows)
  ensureTrailingEmptyRow(draftCookieRows)
  ensureTrailingEmptyRow(draftRequestConfig.body.formData)
  ensureTrailingEmptyRow(draftRequestConfig.body.urlencoded)

  return {
    draftApiPath,
    draftRequestConfig,
    draftHeaderRows,
    draftCookieRows,
    draftPreRequestScript: detail.preRequestScript ?? '',
    draftPostRequestScript: detail.postRequestScript ?? ''
  }
}
