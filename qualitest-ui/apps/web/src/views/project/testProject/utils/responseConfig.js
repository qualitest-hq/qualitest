/**
 * 接口响应配置读写（存接口表 responseConfig 单一 JSON 字符串）。
 * 结构含 configVersion、expectedResponseKind（期望形态：json / xml / html / any）、responses 条目列表。
 * 跑流时用期望形态对照实际响应；调试面板也可据此提示形态不符。
 */

import { RESPONSE_CONFIG_VERSION } from './apiConfigConstants'
import {sanitizeBodyJsonSchemaForPersist} from '@/views/project/testProject/utils/jsonSchemaTree'

export { RESPONSE_CONFIG_VERSION } from './apiConfigConstants'

/** 生成新建响应条目的 id */
export function genResponseEntryId() {
  return `resp-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 9)}`
}

/** 默认一条成功响应条目（200 / json） */
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

/** 空响应配置：版本号 + 期望形态 json + 一条默认成功条目 */
export function createEmptyResponseConfig() {
  return {
    configVersion: RESPONSE_CONFIG_VERSION,
    expectedResponseKind: 'json',
    responses: [createDefaultResponseEntry()]
  }
}

/** 规范条目 contentType 为 json / xml / binary */
function normalizeContentType(v) {
  const x = String(v || '').toLowerCase()
  if (x === 'xml' || x === 'binary' || x === 'json') return x
  return 'json'
}

/**
 * 规范期望响应形态为 json / xml / html / any；空白或未知视为 json。
 * @param {unknown} v
 */
export function normalizeExpectedResponseKind(v) {
  const x = String(v || '').toLowerCase().trim()
  if (x === 'html' || x === 'any' || x === 'json' || x === 'xml') return x
  return 'json'
}

/** 规范单条响应条目字段 */
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

/**
 * 无显式期望时按条目 contentType 推断：全是 json → json，全是 xml → xml，混合或出现 binary → any。
 */
function inferExpectedKindFromResponses(list) {
  if (!Array.isArray(list) || !list.length) return 'json'
  let sawJson = false
  let sawXml = false
  for (const r of list) {
    const ct = normalizeContentType(r?.contentType)
    if (ct === 'json') sawJson = true
    else if (ct === 'xml') sawXml = true
    else return 'any'
  }
  if (sawXml) return sawJson ? 'any' : 'xml'
  return 'json'
}

/**
 * 规范整份响应配置：补齐版本、期望形态、responses。
 * 有 expectedResponseKind 则规范化；否则按条目 contentType 推断。
 */
function normalizeBundle(o) {
  const src = o && typeof o === 'object' ? o : {}
  let list = Array.isArray(src.responses) ? src.responses.map(normalizeResponseEntry) : []
  if (!list.length) list = [createDefaultResponseEntry()]
  const kindRaw = src.expectedResponseKind
  const expectedResponseKind =
    kindRaw != null && String(kindRaw).trim() !== ''
      ? normalizeExpectedResponseKind(kindRaw)
      : inferExpectedKindFromResponses(list)
  return {
    configVersion: RESPONSE_CONFIG_VERSION,
    expectedResponseKind,
    responses: list
  }
}

/**
 * 解析库中或编辑区的 responseConfig 原文。
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

/** 清洗响应 example：空串/空对象置 null，JSON 字符串尽量解析为对象 */
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
 * 序列化为写入库的 JSON 字符串（含每条响应 schema 清洗）。
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

/**
 * 判断实际响应正文是否符合期望形态。
 * any 恒通过；json 要求可解析为 JSON；xml 要求按规则判定为合法 XML；
 * html 要求既非 JSON 也非 XML（形态为 nonJson）。
 *
 * @param {string} expectedKind 期望形态
 * @param {string|null|undefined} bodyText 实际响应正文
 * @param {Record<string, unknown>|null|undefined} [headers] 响应头，用于读取 Content-Type
 */
export function matchesExpectedResponseKind(expectedKind, bodyText, headers) {
  const expected = normalizeExpectedResponseKind(expectedKind)
  if (expected === 'any') return true
  const actual = detectActualResponseKind(bodyText, headers)
  if (expected === 'html') return actual === 'nonJson'
  if (expected === 'xml') return actual === 'xml'
  return actual === 'json'
}

/**
 * 从响应头读取 Content-Type 主类型（小写、去掉 charset 等参数）。
 * 找不到时返回空串。
 */
function contentTypeFromHeaders(headers) {
  if (!headers || typeof headers !== 'object') return ''
  for (const k of Object.keys(headers)) {
    if (k.toLowerCase() === 'content-type' && headers[k] != null) {
      return String(headers[k]).split(';')[0].trim().toLowerCase()
    }
  }
  return ''
}

/**
 * 是否应按 XML 判定正文。
 * Content-Type 为 text/xml、application/xml 或以 +xml 结尾时为是；
 * 未声明或为 text/plain 时，再看正文是否以 <?xml、<xml>、<xml 开头。
 */
function shouldTryXml(trimmed, contentType) {
  if (contentType === 'text/xml' || contentType === 'application/xml' || contentType.endsWith('+xml')) {
    return true
  }
  if (contentType && contentType !== 'text/plain') return false
  return trimmed.startsWith('<?xml') || trimmed.startsWith('<xml>') || trimmed.startsWith('<xml ')
}

/**
 * 正文是否为可解析的 XML。
 * 含 DOCTYPE 时直接视为非法；无 DOMParser 时返回 false。
 */
function isWellFormedXml(text) {
  if (/<!DOCTYPE/i.test(text) || typeof DOMParser === 'undefined') return false
  try {
    const doc = new DOMParser().parseFromString(text, 'application/xml')
    return doc.getElementsByTagName('parsererror').length === 0
  } catch {
    return false
  }
}

/**
 * 根据响应正文与可选响应头判定实际形态。
 * 以大括号/方括号开头且可 JSON.parse → json；
 * 满足 XML 判定且可解析 → xml；否则 nonJson。
 *
 * @param {string|null|undefined} bodyText 响应原文
 * @param {Record<string, unknown>|null|undefined} [headers] 响应头
 * @returns {'json'|'xml'|'nonJson'}
 */
export function detectActualResponseKind(bodyText, headers) {
  if (bodyText == null || String(bodyText).trim() === '') return 'nonJson'
  const trimmed = String(bodyText).replace(/^\uFEFF/, '').trim()
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      JSON.parse(trimmed)
      return 'json'
    } catch {
      return 'nonJson'
    }
  }
  if (shouldTryXml(trimmed, contentTypeFromHeaders(headers)) && isWellFormedXml(trimmed)) {
    return 'xml'
  }
  return 'nonJson'
}
