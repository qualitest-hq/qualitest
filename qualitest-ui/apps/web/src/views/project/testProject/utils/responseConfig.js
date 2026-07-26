/**
 * API 响应配置读写（存 TestProjectApi.responseConfig 单一 JSON 字符串）。
 * 结构：{ configVersion: 1, responses: [ { id, name, httpStatus, contentType, schema, example, ... } ] }
 */

import { RESPONSE_CONFIG_VERSION } from './apiConfigV2Constants'
import {sanitizeBodyJsonSchemaForPersist} from '@/views/project/testProject/utils/jsonSchemaTree'

export { RESPONSE_CONFIG_VERSION } from './apiConfigV2Constants'

/** 生成 Web 端新建响应项时使用的 id */
export function genResponseEntryId() {
  return `resp-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 9)}`
}

export function createDefaultResponseEntry() {
  return {
    id: genResponseEntryId(),
    name: '成功',
    httpStatus: 200,
    contentType: 'json',
    schema: null,
    example: null,
    xmlText: '',
    binaryNote: ''
  }
}

export function createEmptyResponseConfig() {
  return {
    configVersion: RESPONSE_CONFIG_VERSION,
    responses: [createDefaultResponseEntry()]
  }
}

function normalizeContentType(v) {
  const x = String(v || '').toLowerCase()
  if (x === 'xml' || x === 'binary' || x === 'json') return x
  return 'json'
}

function normalizeResponseEntry(r) {
  if (!r || typeof r !== 'object') return createDefaultResponseEntry()
  const http = Number(r.httpStatus)
  const ct = normalizeContentType(r.contentType)
  let schema = r.schema !== undefined ? r.schema : null
  let example = r.example !== undefined ? r.example : null
  if (ct === 'json' && example != null) {
    example = sanitizeExampleValue(example)
  }
  return {
    id: typeof r.id === 'string' && r.id ? r.id : genResponseEntryId(),
    name: r.name != null && String(r.name).trim() ? String(r.name).trim() : '成功',
    httpStatus: Number.isFinite(http) && http >= 100 && http <= 599 ? http : 200,
    contentType: ct,
    schema,
    example,
    xmlText: r.xmlText != null ? String(r.xmlText) : '',
    binaryNote: r.binaryNote != null ? String(r.binaryNote) : ''
  }
}

function normalizeBundle(o) {
  const src = o && typeof o === 'object' ? o : {}
  let list = Array.isArray(src.responses) ? src.responses.map(normalizeResponseEntry) : []
  if (!list.length) list = [createDefaultResponseEntry()]
  return {
    configVersion: RESPONSE_CONFIG_VERSION,
    responses: list
  }
}

/**
 * @param {string|null|undefined} raw
 * @returns {{ ok: boolean, bundle: object, rawFallback: string|null, parseError: boolean }}
 */
export function parseResponseConfigInput(raw) {
  if (raw == null || raw === '') {
    return {ok: true, bundle: createEmptyResponseConfig(), rawFallback: null, parseError: false}
  }
  const str = typeof raw === 'string' ? raw.trim() : JSON.stringify(raw)
  let parsed
  try {
    parsed = typeof raw === 'object' && raw !== null ? raw : JSON.parse(str)
  } catch {
    return {ok: false, bundle: createEmptyResponseConfig(), rawFallback: str, parseError: true}
  }
  if (
      parsed &&
      typeof parsed === 'object' &&
      !Array.isArray(parsed) &&
      Array.isArray(parsed.responses)
  ) {
    return {ok: true, bundle: normalizeBundle(parsed), rawFallback: null, parseError: false}
  }
  return {ok: false, bundle: createEmptyResponseConfig(), rawFallback: str, parseError: true}
}

function sanitizeExampleValue(ex) {
  if (ex == null) return null
  if (typeof ex === 'string') {
    const t = ex.trim()
    if (!t) return null
    try {
      return JSON.parse(t)
    } catch {
      return t
    }
  }
  if (typeof ex === 'object' && !Array.isArray(ex) && Object.keys(ex).length === 0) return null
  return ex
}

/**
 * 序列化为写入库的 JSON 字符串（含每条响应 schema 清洗；unwrap 仅展示，写库保持扫描形态）
 */
export function serializeResponseConfig(bundle) {
  const out = normalizeBundle(JSON.parse(JSON.stringify(bundle)))
  for (const r of out.responses) {
    if (r.contentType === 'json') {
      r.schema = sanitizeBodyJsonSchemaForPersist(r.schema)
      r.example = sanitizeExampleValue(r.example)
    } else if (r.contentType === 'xml') {
      r.xmlText = (r.xmlText || '').trim()
      r.schema = null
      r.example = null
      r.binaryNote = ''
    } else {
      r.binaryNote = (r.binaryNote || '').trim()
      r.schema = null
      r.example = null
      r.xmlText = ''
    }
  }
  return JSON.stringify(out)
}
