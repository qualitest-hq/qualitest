/**
 * 预制接口与请求/响应工作台之间的适配（对象草稿，不依赖项目主键）。
 * 负责：预制接口 ↔ 工作台详情形状互转、鉴权 mode 补丁、设计提示文本互转、响应配置编辑文本。
 */

import { parseFlexibleJson } from '../../testProject/utils/apiDetailRequestWorkbench'

/** 把 JSON 字符串或对象解析成对象；失败返回 fallback */
export function parsePersistJsonColumn(raw, fallback = {}) {
  const parsed = parseFlexibleJson(raw)
  if (parsed && typeof parsed === 'object') return parsed
  return fallback
}

/** 预制接口对象转成工作台用的 apiDetail 形状（不造假主键） */
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
 * 把工作台保存结果写回预制接口对象。
 * 要求 payload 已含拆好的 testValueConfig（结构里不再带调试测值）。
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
  if (persistPart.testValueConfig == null) {
    return { ok: false, error: '缺少 testValueConfig（须先 peel）' }
  }

  return {
    ok: true,
    api: {
      ...api,
      apiPath: persistPart.apiPath != null ? String(persistPart.apiPath) : api.apiPath,
      requestConfig: parsePersistJsonColumn(persistPart.requestConfig, {}),
      responseConfig: persistPart.responseConfig != null
        ? parsePersistJsonColumn(persistPart.responseConfig, {})
        : (api.responseConfig ?? {}),
      testValueConfig: parsePersistJsonColumn(persistPart.testValueConfig, {}),
      headers: parsePersistJsonColumn(persistPart.headers, {}),
      cookies: parsePersistJsonColumn(persistPart.cookies, {}),
      preRequestScript: persistPart.preRequestScript ?? api.preRequestScript ?? null,
      postRequestScript: persistPart.postRequestScript ?? api.postRequestScript ?? null,
    },
  }
}

/** responseConfig 对象格式化为编辑器里的缩进 JSON 文本 */
export function responseConfigToEditorText(responseConfig) {
  const obj = parseFlexibleJson(responseConfig)
  if (!obj || typeof obj !== 'object') {
    return JSON.stringify({ configVersion: 1, responses: [] }, null, 2)
  }
  return JSON.stringify(obj, null, 2)
}

/**
 * 编辑器 JSON 文本解析成 responseConfig 对象。
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

/** 读写鉴权 mode=override 时的自定义头（name / valueTemplate）。 */
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

/** 更新鉴权 mode；override 时写入自定义头，其它 mode 清掉头；预制接口不写 authProfileId。 */
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
  // 预制接口不挂项目 Profile id
  delete next.authProfileId
  return next
}
