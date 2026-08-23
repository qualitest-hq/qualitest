/**
 * 把请求/响应结构里的调试测值拆到 testValueConfig，结构只留定义。
 * 用于调试保存、设计保存、预制口编辑等写路径。
 * 读路径用 applyTestValuesToStructure 把测值叠回结构（对称）。
 * 画布侧 applyRequestValueOverridesToWorkbench 亦委托本函数，避免双份叠回逻辑。
 */
import { parseFlexibleJson } from './apiDetailRequestWorkbench'

/** 结构里带 value 的参数数组字段 */
const PARAM_ARRAY_FIELDS = ['queryParams', 'pathParams', 'declaredHeaders']

/**
 * 剥离测值并返回三份对象（会改动入参的拷贝结果）。
 * @param {object|string|null} requestConfig 可能带 value / body.example
 * @param {object|string|null} responseConfig 可能带 responses[].example
 * @param {object|string|null} existingTestValue 已有测值，作合并底稿
 * @returns {{ requestConfig: object, responseConfig: object, testValueConfig: object }}
 */
export function peelTestValuesFromStructure(requestConfig, responseConfig, existingTestValue) {
  const testRoot = asObject(parseFlexibleJson(existingTestValue)) || {}
  const testRequest = ensureObj(testRoot, 'request')
  const testResponse = ensureObj(testRoot, 'response')

  const request = asObject(parseFlexibleJson(requestConfig)) || {}
  peelRequest(request, testRequest)

  const response = asObject(parseFlexibleJson(responseConfig)) || {}
  peelResponse(response, testResponse)

  return {
    requestConfig: request,
    responseConfig: response,
    testValueConfig: testRoot,
  }
}

/**
 * peel 的逆操作：把 testValueConfig.request 叠回 requestConfig（就地修改）。
 * paramDefaults → 各参数数组 value；bodyExample → body.json.example。
 * @param {object|null} requestConfig 工作台草稿 requestConfig
 * @param {object|string|null} testValueConfig
 * @returns {object} 同一份 requestConfig（无测值时原样返回）
 */
export function applyTestValuesToStructure(requestConfig, testValueConfig) {
  const request = asObject(requestConfig)
  if (!request) return requestConfig
  const testRoot = asObject(parseFlexibleJson(testValueConfig))
  const testRequest = asObject(testRoot?.request)
  if (!testRequest) return request

  const params = asObject(testRequest.paramDefaults)
  if (params && Object.keys(params).length) {
    for (const field of PARAM_ARRAY_FIELDS) {
      applyParamDefaultsToArray(ensureParamArray(request, field), params)
    }
    const body = ensureObj(request, 'body')
    applyParamDefaultsToArray(ensureParamArray(body, 'formData'), params)
    applyParamDefaultsToArray(ensureParamArray(body, 'urlencoded'), params)
  }

  if (Object.prototype.hasOwnProperty.call(testRequest, 'bodyExample')) {
    applyBodyExampleToRequest(request, testRequest.bodyExample)
  }
  return request
}

/** 非数组普通对象，否则 null */
function asObject(v) {
  return v && typeof v === 'object' && !Array.isArray(v) ? v : null
}

/** 保证 parent[key] 是对象并返回 */
function ensureObj(parent, key) {
  if (!parent[key] || typeof parent[key] !== 'object' || Array.isArray(parent[key])) {
    parent[key] = {}
  }
  return parent[key]
}

/** 请求：参数 value → paramDefaults，body.json.example → bodyExample，并从结构删除 */
function peelRequest(request, testRequest) {
  const paramDefaults = ensureObj(testRequest, 'paramDefaults')
  for (const field of PARAM_ARRAY_FIELDS) {
    peelParamArray(request[field], paramDefaults)
  }
  const body = asObject(request.body)
  if (body) {
    peelParamArray(body.formData, paramDefaults)
    peelParamArray(body.urlencoded, paramDefaults)
    const jsonPart = asObject(body.json)
    if (jsonPart && jsonPart.example != null) {
      testRequest.bodyExample = cloneJson(jsonPart.example)
      delete jsonPart.example
    }
  }
  if (!Object.keys(paramDefaults).length) {
    delete testRequest.paramDefaults
  }
}

/** 响应：有 id 的条目把 example 写入 examplesById，并从结构删除 */
function peelResponse(response, testResponse) {
  const examplesById = ensureObj(testResponse, 'examplesById')
  const list = Array.isArray(response.responses) ? response.responses : []
  for (const entry of list) {
    if (!entry || typeof entry !== 'object') continue
    const id = entry.id != null ? String(entry.id).trim() : ''
    if (id && entry.example != null) {
      examplesById[id] = cloneJson(entry.example)
      delete entry.example
    }
  }
  if (!Object.keys(examplesById).length) {
    delete testResponse.examplesById
  }
}

/** 参数数组：非空 value 写入 paramDefaults，并删掉行上的 value */
function peelParamArray(arr, paramDefaults) {
  if (!Array.isArray(arr) || !paramDefaults) return
  for (const row of arr) {
    if (!row || typeof row !== 'object') continue
    const name = String(row.name || '').trim()
    if (!name || row.value == null) continue
    const value = typeof row.value === 'string' ? row.value : JSON.stringify(row.value)
    if (String(value).trim()) {
      paramDefaults[name] = row.value
    }
    delete row.value
  }
}

/** 深拷贝 JSON 可序列化值 */
function cloneJson(v) {
  try {
    return JSON.parse(JSON.stringify(v))
  } catch {
    return v
  }
}

/** 保证 parent[key] 是数组并返回 */
function ensureParamArray(parent, key) {
  if (!Array.isArray(parent[key])) {
    parent[key] = []
  }
  return parent[key]
}

/** 按参数名把 defaults 写进 KV 行；没有对应行则追加 */
function applyParamDefaultsToArray(rows, params) {
  if (!Array.isArray(rows) || !params) return
  for (const [name, value] of Object.entries(params)) {
    const key = String(name || '').trim()
    if (!key) continue
    const row = rows.find((r) => r && String(r.name || '').trim() === key)
    if (row) {
      row.value = value
      if (row._enabled === false) row._enabled = true
    } else {
      rows.push({
        name: key,
        value,
        type: 'string',
        _enabled: true,
      })
    }
  }
}

/** 把 bodyExample 写入 body.json.example；mode 为 none/空时切到 json */
function applyBodyExampleToRequest(request, bodyExample) {
  const body = ensureObj(request, 'body')
  if (!body.mode || body.mode === 'none') {
    body.mode = 'json'
  }
  if (body.mode !== 'json') return
  const jsonPart = asObject(body.json) || {}
  jsonPart.example = cloneJson(bodyExample)
  body.json = jsonPart
}
