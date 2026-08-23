/**
 * 预制接口 ↔ 请求/响应工作台适配（对象草稿，无项目 ID）。
 */

import { parseFlexibleJson } from '../../testProject/utils/apiDetailRequestWorkbench'

/** 解析 JSON 字符串列为对象；已是对象则深拷贝；失败返回 fallback */
export function parsePersistJsonColumn(raw, fallback = {}) {
  const parsed = parseFlexibleJson(raw)
  if (parsed && typeof parsed === 'object') return parsed
  return fallback
}

/** 预制对象 → 工作台 apiDetail（不造假 ID） */
export function prefabToWorkbenchDetail(api) {
  if (!api || typeof api !== 'object') {
    return {
      apiPath: '',
      requestConfig: null,
      headers: {},
      cookies: {},
      preRequestScript: '',
      postRequestScript: '',
    }
  }
  return {
    apiName: api.apiName ?? '',
    apiPath: api.apiPath ?? '',
    apiGroup: api.apiGroup ?? '',
    apiDescription: api.apiDescription ?? '',
    protocolType: api.protocolType ?? 'http',
    apiStatus: api.apiStatus ?? 'normal',
    requestConfig: api.requestConfig ?? null,
    headers: api.headers ?? {},
    cookies: api.cookies ?? {},
    responseConfig: api.responseConfig ?? null,
    preRequestScript: api.preRequestScript ?? '',
    postRequestScript: api.postRequestScript ?? '',
    authConfig: api.authConfig ?? { mode: 'inherit' },
    designHints: api.designHints ?? { hints: [] },
    testValueConfig: api.testValueConfig ?? {},
  }
}

/**
 * 把 buildPersistPayload 的字符串列合并进预制口。
 * @returns {{ ok: true, api: object } | { ok: false, error: string }}
 */
export function applyWorkbenchPersistToPrefab(api, persistPart) {
  if (!api || typeof api !== 'object') {
    return { ok: false, error: '预制接口无效' }
  }
  if (!persistPart || typeof persistPart !== 'object') {
    return { ok: false, error: '请求草稿无效' }
  }
  if (persistPart.error) {
    return { ok: false, error: String(persistPart.error) }
  }

  const next = {
    ...api,
    apiPath: persistPart.apiPath != null ? String(persistPart.apiPath) : api.apiPath,
    requestConfig: parsePersistJsonColumn(persistPart.requestConfig, {}),
    headers: parsePersistJsonColumn(persistPart.headers, {}),
    cookies: parsePersistJsonColumn(persistPart.cookies, {}),
    preRequestScript: persistPart.preRequestScript ?? api.preRequestScript ?? null,
    postRequestScript: persistPart.postRequestScript ?? api.postRequestScript ?? null,
  }
  return { ok: true, api: next }
}

/** responseConfig 对象 → 面板用的 JSON 字符串 */
export function responseConfigToEditorText(responseConfig) {
  const obj = parseFlexibleJson(responseConfig)
  if (!obj || typeof obj !== 'object') {
    return JSON.stringify({ configVersion: 1, responses: [] }, null, 2)
  }
  return JSON.stringify(obj, null, 2)
}

/**
 * 面板 JSON 字符串写回预制 responseConfig 对象。
 * @returns {{ ok: true, value: object } | { ok: false, error: string }}
 */
export function parseResponseConfigEditorText(text) {
  const trimmed = String(text || '').trim()
  if (!trimmed) {
    return { ok: true, value: { configVersion: 1, responses: [] } }
  }
  try {
    const parsed = JSON.parse(trimmed)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { ok: false, error: '响应配置须为 JSON 对象' }
    }
    return { ok: true, value: parsed }
  } catch (e) {
    return { ok: false, error: String(e?.message || '响应配置 JSON 无效') }
  }
}

/** designHints.hints 数组 ↔ 换行文本 */
export function designHintsToText(designHints) {
  const obj = parseFlexibleJson(designHints)
  const hints = Array.isArray(obj?.hints) ? obj.hints : Array.isArray(obj) ? obj : []
  return hints.map((h) => String(h ?? '').trim()).filter(Boolean).join('\n')
}

export function designHintsFromText(text) {
  const hints = String(text ?? '')
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean)
  return { hints }
}

/** 鉴权 override 头字段读写（与设计页结构一致：authConfig.header） */
export function readAuthOverrideHeader(authConfig) {
  const cfg = parseFlexibleJson(authConfig) || {}
  const header = cfg.header && typeof cfg.header === 'object' ? cfg.header : {}
  return {
    headerName: header.name != null && String(header.name).trim()
      ? String(header.name).trim()
      : 'Authorization',
    valueTemplate: header.valueTemplate != null ? String(header.valueTemplate).trim() : '',
  }
}

export function patchAuthConfigMode(authConfig, mode, overrideHeader) {
  const prev = parseFlexibleJson(authConfig) || {}
  const nextMode = String(mode || 'inherit').trim() || 'inherit'
  const next = { ...prev, mode: nextMode }
  if (nextMode === 'override' && overrideHeader) {
    const name = String(overrideHeader.headerName || '').trim() || 'Authorization'
    const valueTemplate = String(overrideHeader.valueTemplate || '').trim()
    next.header = { name, valueTemplate }
  } else if (nextMode !== 'override') {
    delete next.header
  }
  // 预制口不写 authProfileId
  delete next.authProfileId
  return next
}
