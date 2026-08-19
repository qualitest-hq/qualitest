/**
 * 项目级鉴权配置（auth_config）表单解析 / 组装 / 轻量校验。
 * 对齐后端 ProjectAuthConfig：扁平头 + credentialApi + loginHint + apis[]。
 */

export const LOGIN_HINT_FROM_OPTIONS = [
  { label: 'body', value: 'body' },
  { label: 'setCookie', value: 'setCookie' },
  { label: 'header', value: 'header' },
]

const LOGIN_HINT_FROM_VALUES = LOGIN_HINT_FROM_OPTIONS.map((o) => o.value)

/** 空表单（无 Profile） */
export function emptyAuthForm() {
  return {
    profiles: [],
    needsAuthTemplateHint: false,
  }
}

/** 新建一条空白 Profile 行 */
export function emptyProfileRow() {
  return {
    id: '',
    name: '',
    pathPrefixText: '',
    headerName: 'Authorization',
    valueTemplate: 'Bearer {{flow.token}}',
    credentialMethod: 'POST',
    credentialPath: '',
    loginFlowKey: '',
    loginFrom: 'body',
    loginExpr: '',
    apis: [],
  }
}

function str(v, fallback = '') {
  return v != null && String(v).trim() !== '' ? String(v) : fallback
}

/** pathPrefix 多行/逗号文本 → 非空列表 */
export function splitPathLines(text) {
  return String(text || '')
    .split(/[\n,，]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

function joinPathLines(list) {
  if (!Array.isArray(list) || !list.length) return ''
  return list.map((s) => String(s).trim()).filter(Boolean).join('\n')
}

/** JSON 字符串或对象；非法则 null */
export function parseJsonMaybe(raw) {
  if (raw == null || raw === '') return null
  if (typeof raw === 'object') return raw
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch {
      return null
    }
  }
  return null
}

function resolveHeaderName(p) {
  return str(p?.headerName, 'Authorization')
}

function resolveHeaderValueTemplate(p) {
  return str(p?.headerValueTemplate)
}

function resolveLoginHint(p) {
  if (p?.loginHint && typeof p.loginHint === 'object') {
    return p.loginHint
  }
  return null
}

function resolveApiMethod(api) {
  const cfg = parseJsonMaybe(api?.requestConfig)
  return str(cfg?.method, 'GET').toUpperCase()
}

function apiToRow(api) {
  return {
    method: resolveApiMethod(api),
    apiPath: str(api?.apiPath),
    apiName: str(api?.apiName),
    authMode: str(api?.authConfig?.mode, 'inherit'),
  }
}

function profileToRow(p) {
  const hint = resolveLoginHint(p)
  const credential = p?.credentialApi || {}
  const apis = Array.isArray(p?.apis) ? p.apis.map(apiToRow) : []
  return {
    id: str(p?.id),
    name: str(p?.name),
    pathPrefixText: joinPathLines(p?.match?.pathPrefix),
    headerName: resolveHeaderName(p),
    valueTemplate: resolveHeaderValueTemplate(p),
    credentialMethod: str(credential?.method, 'POST').toUpperCase(),
    credentialPath: str(credential?.path),
    loginFlowKey: str(hint?.flowKey),
    loginFrom: str(hint?.from, 'body'),
    loginExpr: str(hint?.expr),
    apis,
  }
}

/**
 * 把库中 authConfig（JSON 字符串或对象）填入表单结构。
 */
export function parseAuthConfig(raw, options = {}) {
  const form = emptyAuthForm()
  form.needsAuthTemplateHint = !!options.needsAuthTemplateHint

  const obj = parseJsonMaybe(raw)
  if (!obj || typeof obj !== 'object') {
    return form
  }

  const profiles = Array.isArray(obj.authProfiles) ? obj.authProfiles.map(profileToRow) : []
  form.profiles = profiles

  if (!form.needsAuthTemplateHint && profiles.length) {
    form.needsAuthTemplateHint = profiles.some((p) => !p.apis?.length)
  }

  return form
}

/**
 * 轻量校验；通过返回 null，失败返回可读文案。
 */
export function validateAuthForm(form) {
  const profiles = form?.profiles || []
  const ids = new Set()
  for (let i = 0; i < profiles.length; i++) {
    const p = profiles[i]
    const id = String(p?.id || '').trim()
    if (!id) {
      return `第 ${i + 1} 个 Profile 的 id 不能为空`
    }
    if (ids.has(id)) {
      return `Profile id 重复: ${id}`
    }
    ids.add(id)
    const headerName = String(p?.headerName || '').trim()
    const valueTemplate = String(p?.valueTemplate || '').trim()
    if (!headerName || !valueTemplate) {
      return `Profile「${id}」须填写头名称与值模板`
    }
    if (splitPathLines(p?.pathPrefixText).some((x) => x === '/')) {
      return `Profile「${id}」的 pathPrefix 禁止使用 "/"`
    }
    const from = String(p?.loginFrom || '').trim()
    if (from && !LOGIN_HINT_FROM_VALUES.includes(from)) {
      return `Profile「${id}」的 loginHint.from 仅支持 body / setCookie / header`
    }
    const credentialPath = String(p?.credentialPath || '').trim()
    if (credentialPath && !String(p?.credentialMethod || '').trim()) {
      return `Profile「${id}」填写 credentialApi.path 时须同时填写 method`
    }
  }
  return null
}

function rowToProfile(p) {
  const id = String(p.id || '').trim()
  const name = String(p.name || '').trim()
  const prefixes = splitPathLines(p.pathPrefixText)
  const headerName = String(p.headerName || '').trim()
  const valueTemplate = String(p.valueTemplate || '').trim()
  const flowKey = String(p.loginFlowKey || '').trim()
  const from = String(p.loginFrom || '').trim()
  const expr = String(p.loginExpr || '').trim()
  const credentialMethod = String(p.credentialMethod || '').trim().toUpperCase()
  const credentialPath = String(p.credentialPath || '').trim()

  const row = {
    id,
    headerName,
    headerValueTemplate: valueTemplate,
  }
  if (name) row.name = name
  if (prefixes.length) {
    row.match = { pathPrefix: prefixes }
  }
  if (credentialPath) {
    row.credentialApi = {
      method: credentialMethod || 'POST',
      path: credentialPath,
    }
  }
  if (flowKey || from || expr) {
    row.loginHint = {}
    if (flowKey) row.loginHint.flowKey = flowKey
    if (from) row.loginHint.from = from
    if (expr) row.loginHint.expr = expr
  }
  if (Array.isArray(p.apis) && p.apis.length) {
    row.apis = p.apis
      .filter((api) => String(api?.apiPath || '').trim())
      .map((api) => ({
        apiName: String(api.apiName || '').trim() || undefined,
        apiPath: String(api.apiPath || '').trim(),
        authConfig: {
          mode: String(api.authMode || 'inherit').trim() || 'inherit',
        },
        requestConfig: {
          configVersion: 1,
          method: String(api.method || 'GET').trim().toUpperCase() || 'GET',
        },
      }))
  }
  return row
}

/**
 * 表单 → 可落库对象；空配置返回 null。
 */
export function buildAuthConfigObject(form) {
  const err = validateAuthForm(form)
  if (err) {
    throw new Error(err)
  }
  const profiles = (form?.profiles || [])
    .map(rowToProfile)
    .filter((p) => p.id)

  if (!profiles.length) {
    return null
  }

  return { authProfiles: profiles }
}

/**
 * 表单 → 可落库 JSON 字符串（空配置为 "{}"）。
 */
export function buildAuthConfigPayload(form) {
  const obj = buildAuthConfigObject(form)
  return obj == null ? '{}' : JSON.stringify(obj)
}

/** 预览用：美化 JSON；非法时返回错误文案 */
export function formatAuthConfigPreview(form) {
  try {
    const obj = buildAuthConfigObject(form)
    return JSON.stringify(obj == null ? {} : obj, null, 2)
  } catch (e) {
    return String(e?.message || e)
  }
}

/** 预制接口 authMode 展示文案 */
export function formatAuthModeLabel(mode) {
  const m = String(mode || 'inherit').trim().toLowerCase()
  if (m === 'none') return '免登录'
  if (m === 'override') return '自定义'
  return '继承'
}
