/**
 * 预制接口 JSON 草稿解析与提交前校验（面板与单测共用）。
 */

import {
  replaceApiAtIndex,
  syncMethodFromRequestConfig,
} from './templateForm'

const JSON_TABS = ['request', 'response', 'testValue', 'raw']

const JSON_DRAFT_FIELD_MAP = {
  request: 'requestConfig',
  response: 'responseConfig',
  testValue: 'testValueConfig',
}

const JSON_DRAFT_TAB_LABELS = [
  { name: 'request', label: '请求' },
  { name: 'response', label: '响应' },
  { name: 'testValue', label: '测参' },
  { name: 'raw', label: '原始 JSON' },
]

/** 解析 JSON 文本；空串视为 {} */
export function tryParseJsonText(text) {
  const trimmed = String(text || '').trim()
  if (!trimmed) return { ok: true, value: {} }
  try {
    return { ok: true, value: JSON.parse(trimmed) }
  } catch (e) {
    return { ok: false, error: String(e?.message || 'JSON 格式错误') }
  }
}

/** 原始 JSON Tab：须为对象 */
export function parseRawApiObject(text) {
  const parsed = tryParseJsonText(text)
  if (!parsed.ok) return parsed
  if (!parsed.value || typeof parsed.value !== 'object' || Array.isArray(parsed.value)) {
    return { ok: false, error: '预制接口须为 JSON 对象' }
  }
  return parsed
}

export function formatJsonSection(value, fallback = {}) {
  const obj = value && typeof value === 'object' ? value : fallback
  return JSON.stringify(obj, null, 2)
}

export function emptyJsonDraftState() {
  return {
    drafts: { request: '{}', response: '{}', testValue: '{}', raw: '{}' },
    errors: { request: '', response: '', testValue: '', raw: '' },
  }
}

/** 单条 api → 各 Tab 草稿文本 */
export function apiToJsonDrafts(api) {
  if (!api) return emptyJsonDraftState().drafts
  return {
    request: formatJsonSection(api.requestConfig, {}),
    response: formatJsonSection(api.responseConfig, {}),
    testValue: formatJsonSection(api.testValueConfig, {}),
    raw: JSON.stringify(api, null, 2),
  }
}

/**
 * JSON Tab 编辑落盘。
 * @returns {{ ok: true, apis: object[], draftPatch?: Record<string,string>, errors: Record<string,string> } | { ok: false, errors: Record<string,string> }}
 */
export function applyPrefabJsonDraftChange({ tab, text, apis, selectedIndex }) {
  const errors = { request: '', response: '', testValue: '', raw: '' }
  if (selectedIndex < 0 || selectedIndex >= apis.length) {
    return { ok: false, errors: { ...errors, [tab]: '接口索引无效' } }
  }

  const parsed = tab === 'raw' ? parseRawApiObject(text) : tryParseJsonText(text)
  if (!parsed.ok) {
    return { ok: false, errors: { ...errors, [tab]: parsed.error } }
  }

  if (tab === 'raw') {
    try {
      return {
        ok: true,
        apis: replaceApiAtIndex(apis, selectedIndex, parsed.value),
        errors,
      }
    } catch (e) {
      return { ok: false, errors: { ...errors, raw: String(e?.message || e) } }
    }
  }

  const field = JSON_DRAFT_FIELD_MAP[tab]
  const current = apis[selectedIndex]
  let nextApi = { ...current, [field]: parsed.value }
  if (tab === 'request') {
    nextApi = syncMethodFromRequestConfig(nextApi, parsed.value)
  }
  const nextApis = apis.map((item, idx) => (idx === selectedIndex ? nextApi : item))
  const draftPatch = tab === 'request'
    ? { request: formatJsonSection(nextApi.requestConfig, {}) }
    : undefined
  return { ok: true, apis: nextApis, draftPatch, errors }
}

/**
 * 提交前校验：apis 非空 + 各 Tab JSON 合法。
 */
export function validatePrefabApiDrafts({ apis, jsonDrafts, jsonErrors }) {
  if (!Array.isArray(apis) || !apis.length) {
    return { valid: false, message: '预制接口不能为空' }
  }
  const pending = Object.entries(jsonErrors || {}).find(([, msg]) => !!msg)
  if (pending) {
    return { valid: false, message: `接口 JSON 无效：${pending[1]}` }
  }
  for (const tab of JSON_TABS) {
    const text = jsonDrafts?.[tab]
    if (tab === 'raw') {
      const parsed = parseRawApiObject(text)
      if (!parsed.ok) {
        return { valid: false, message: `接口 JSON 无效：${parsed.error}` }
      }
      continue
    }
    const parsed = tryParseJsonText(text)
    if (!parsed.ok) {
      return { valid: false, message: `接口 JSON 无效：${parsed.error}` }
    }
  }
  return { valid: true, message: '' }
}

export { JSON_DRAFT_FIELD_MAP, JSON_DRAFT_TAB_LABELS, JSON_TABS }
