/**
 * 将 AI API 设计 patch 变更合并进 apiDetail 草稿（不落库）。
 */
import { pruneConstraintsForType } from './fieldTypeConstraints'

/** 将 JSON 字符串或对象深拷贝为普通对象，解析失败返回 null。 */
function parseJsonMaybe(raw) {
  if (raw == null || raw === '') return null
  if (typeof raw === 'object') {
    try {
      return JSON.parse(JSON.stringify(raw))
    } catch {
      return null
    }
  }
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch {
      return null
    }
  }
  return null
}

function findParamRow(rows, name) {
  if (!Array.isArray(rows) || !name) return null
  const n = String(name).trim()
  return rows.find((r) => r && String(r.name || '').trim() === n) || null
}

/** 确保 detail 含可用的 requestConfig 对象结构。 */
function ensureRequestConfig(detail) {
  let rc = parseJsonMaybe(detail.requestConfig)
  if (!rc || typeof rc !== 'object') {
    rc = {
      configVersion: 1,
      method: 'GET',
      queryParams: [],
      pathParams: [],
      declaredHeaders: [],
      body: { mode: 'none', json: { schema: null, example: null }, formData: [], urlencoded: [] },
    }
  }
  if (!rc.body || typeof rc.body !== 'object') {
    rc.body = { mode: 'none', json: { schema: null, example: null }, formData: [], urlencoded: [] }
  }
  if (!rc.body.json || typeof rc.body.json !== 'object') {
    rc.body.json = { schema: null, example: null }
  }
  if (!Array.isArray(rc.queryParams)) rc.queryParams = []
  if (!Array.isArray(rc.pathParams)) rc.pathParams = []
  if (!Array.isArray(rc.declaredHeaders)) rc.declaredHeaders = []
  if (!Array.isArray(rc.body.formData)) rc.body.formData = []
  if (!Array.isArray(rc.body.urlencoded)) rc.body.urlencoded = []
  return rc
}

/** 确保 detail 含可用的 responseConfig 对象结构。 */
function ensureResponseConfig(detail) {
  let resp = parseJsonMaybe(detail.responseConfig)
  if (!resp || typeof resp !== 'object') {
    resp = { responses: [] }
  }
  if (!Array.isArray(resp.responses)) resp.responses = []
  return resp
}

/** 在 JSON Schema 上按点分路径定位叶子（支持 properties / items） */
function resolveSchemaNode(root, path) {
  if (!root || typeof root !== 'object' || !path) return null
  const parts = String(path)
    .split('.')
    .map((p) => p.trim())
    .filter(Boolean)
  let node = root
  for (const part of parts) {
    if (!node || typeof node !== 'object') return null
    if (part === 'items' && node.items != null) {
      node = node.items
      continue
    }
    const props = node.properties
    if (props && typeof props === 'object' && props[part] != null) {
      node = props[part]
      continue
    }
    return null
  }
  return node
}

function applyConstraintsToObject(target, constraints, type, flatParam) {
  if (!target || !constraints) return
  for (const [k, v] of Object.entries(constraints)) {
    if (v === null || v === undefined || v === '') {
      delete target[k]
    } else {
      target[k] = v
    }
  }
  const t = type || target.type || 'string'
  pruneConstraintsForType(target, t, { flatParam })
}

function applyFlatConstraint(rc, target, change) {
  const map = {
    'request.queryParams': 'queryParams',
    'request.pathParams': 'pathParams',
    'request.declaredHeaders': 'declaredHeaders',
    'request.body.formData': 'formData',
    'request.body.urlencoded': 'urlencoded',
  }
  const key = map[target]
  if (!key) return false
  let rows
  if (key === 'formData' || key === 'urlencoded') {
    rows = rc.body[key]
  } else {
    rows = rc[key]
  }
  if (!Array.isArray(rows)) return false
  let row = findParamRow(rows, change.path)
  if (!row) {
    row = { name: change.path, type: change.type || 'string', value: '' }
    rows.push(row)
  }
  if (change.type) row.type = change.type
  if (change.description != null) row.description = change.description
  applyConstraintsToObject(row, change.constraints || {}, row.type, true)
  return true
}

function applyBodySchemaConstraint(rc, change) {
  if (!rc.body.json.schema || typeof rc.body.json.schema !== 'object') {
    rc.body.json.schema = { type: 'object', properties: {} }
  }
  const node = resolveSchemaNode(rc.body.json.schema, change.path)
  if (!node) return false
  if (change.type) node.type = change.type
  if (change.description != null) node.description = change.description
  applyConstraintsToObject(node, change.constraints || {}, node.type || change.type, false)
  return true
}

function applyResponseSchemaConstraint(resp, change) {
  let entry = null
  if (change.responseId) {
    entry = resp.responses.find((r) => r && String(r.id) === String(change.responseId))
  }
  if (!entry && resp.responses.length) {
    entry = resp.responses[0]
  }
  if (!entry) return false
  if (!entry.schema || typeof entry.schema !== 'object') {
    entry.schema = { type: 'object', properties: {} }
  }
  const node = resolveSchemaNode(entry.schema, change.path)
  if (!node) return false
  if (change.type) node.type = change.type
  if (change.description != null) node.description = change.description
  applyConstraintsToObject(node, change.constraints || {}, node.type || change.type, false)
  return true
}

function applyTestValue(rc, resp, change) {
  const target = change.target
  if (target === 'testValue.request.paramDefaults') {
    const name = change.path
    const pools = [rc.queryParams, rc.pathParams, rc.declaredHeaders, rc.body.formData, rc.body.urlencoded]
    for (const rows of pools) {
      const row = findParamRow(rows, name)
      if (row) {
        row.value = change.action === 'clear' ? '' : change.value != null ? String(change.value) : ''
        return true
      }
    }
    if (!Array.isArray(rc.queryParams)) rc.queryParams = []
    rc.queryParams.push({
      name,
      type: 'string',
      value: change.action === 'clear' ? '' : change.value != null ? String(change.value) : '',
    })
    return true
  }
  if (target === 'testValue.request.bodyExample') {
    rc.body.json.example = change.action === 'clear' ? null : change.value
    return true
  }
  if (target === 'testValue.response.examplesById') {
    const id = change.path
    let entry = resp.responses.find((r) => r && String(r.id) === String(id))
    if (!entry) {
      entry = { id, name: id, schema: { type: 'object', properties: {} }, example: null }
      resp.responses.push(entry)
    }
    entry.example = change.action === 'clear' ? null : change.value
    return true
  }
  return false
}

/**
 * 将已勾选的 patch 变更合并进 apiDetail 草稿（不落库）。
 * @param {object} detail apiDetail
 * @param {object[]} changes 已勾选的 ApiDesignPatchChange
 * @returns {object} 新的 apiDetail 引用
 */
export function applyApiDesignChangesToDetail(detail, changes) {
  if (!detail) return detail
  const next = { ...detail }
  let rc = null
  let resp = null
  let touchRequest = false
  let touchResponse = false

  for (const change of changes || []) {
    if (!change?.target) continue
    const target = change.target

    if (target === 'script') {
      const content = change.action === 'clear' ? '' : (change.content ?? '')
      if (change.phase === 'pre') next.preRequestScript = content
      else if (change.phase === 'post') next.postRequestScript = content
      continue
    }

    if (target === 'meta') {
      const path = change.path || 'apiDescription'
      if (change.action === 'clear') next[path] = ''
      else if (change.content != null) next[path] = change.content
      continue
    }

    if (
      target === 'request.queryParams' ||
      target === 'request.pathParams' ||
      target === 'request.declaredHeaders' ||
      target === 'request.body.formData' ||
      target === 'request.body.urlencoded' ||
      target === 'request.body.schema' ||
      target === 'testValue.request.paramDefaults' ||
      target === 'testValue.request.bodyExample'
    ) {
      if (!rc) rc = ensureRequestConfig(next)
      touchRequest = true
      if (target.startsWith('testValue.')) {
        if (!resp) resp = ensureResponseConfig(next)
        applyTestValue(rc, resp, change)
      } else if (target === 'request.body.schema') {
        applyBodySchemaConstraint(rc, change)
      } else {
        applyFlatConstraint(rc, target, change)
      }
      continue
    }

    if (target === 'response.schema' || target === 'testValue.response.examplesById') {
      if (!resp) resp = ensureResponseConfig(next)
      touchResponse = true
      if (target === 'response.schema') {
        applyResponseSchemaConstraint(resp, change)
      } else {
        if (!rc) rc = ensureRequestConfig(next)
        applyTestValue(rc, resp, change)
      }
    }
  }

  if (touchRequest && rc) {
    next.requestConfig = JSON.stringify(rc)
  }
  if (touchResponse && resp) {
    next.responseConfig = JSON.stringify(resp)
  }
  return next
}
