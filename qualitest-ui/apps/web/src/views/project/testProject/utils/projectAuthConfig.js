/**
 * 项目级鉴权配置（auth_config）表单解析 / 组装 / 模板 / 轻量校验。
 * 字段与后端 ProjectAuthConfig 对齐。
 */

export const LOGIN_HINT_FROM_OPTIONS = [
  { label: 'body', value: 'body' },
  { label: 'setCookie', value: 'setCookie' },
  { label: 'header', value: 'header' },
]

const LOGIN_HINT_FROM_VALUES = LOGIN_HINT_FROM_OPTIONS.map((o) => o.value)

/** 上传空配置时的 RuoYi 单套 Bearer（与后端 ruoyiBearerTemplate 一致） */
export const RUOYI_BEARER_TEMPLATE = Object.freeze({
  defaultProfileId: 'ruoyiBearer',
  authProfiles: [
    {
      id: 'ruoyiBearer',
      name: 'RuoYi Bearer',
      header: {
        name: 'Authorization',
        valueTemplate: 'Bearer {{flow.token}}',
      },
      loginHint: {
        flowKey: 'token',
        from: 'body',
        expr: '$.token',
      },
    },
  ],
  anonymousPathExact: [],
  anonymousPathPrefix: [],
})

/** demo / 商城双端参考模板（与后端双端测试夹具一致） */
export const DUAL_BEARER_TEMPLATE = Object.freeze({
  defaultProfileId: 'adminBearer',
  authProfiles: [
    {
      id: 'clientBearer',
      name: '客户端 Bearer',
      match: { pathPrefix: ['/api/'] },
      header: {
        name: 'Authorization',
        valueTemplate: 'Bearer {{flow.token}}',
      },
      loginHint: {
        flowKey: 'token',
        from: 'body',
        expr: '$.data.token',
      },
    },
    {
      id: 'adminBearer',
      name: '管理端 Bearer',
      match: {
        pathPrefix: ['/system/', '/monitor/', '/tool/', '/web/'],
      },
      header: {
        name: 'Authorization',
        valueTemplate: 'Bearer {{flow.adminToken}}',
      },
      loginHint: {
        flowKey: 'adminToken',
        from: 'body',
        expr: '$.token',
      },
    },
  ],
  anonymousPathExact: ['/login', '/register', '/captchaImage'],
  anonymousPathPrefix: ['/test-support/', '/swagger-ui', '/v3/api-docs'],
})

/** 空表单（无 Profile） */
export function emptyAuthForm() {
  return {
    defaultProfileId: '',
    profiles: [],
    anonymousPathExactText: '',
    anonymousPathPrefixText: '',
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
    loginFlowKey: '',
    loginFrom: 'body',
    loginExpr: '',
  }
}

function str(v, fallback = '') {
  return v != null && String(v).trim() !== '' ? String(v) : fallback
}

function splitPathLines(text) {
  return String(text || '')
    .split(/[\n,，]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

function joinPathLines(list) {
  if (!Array.isArray(list) || !list.length) return ''
  return list.map((s) => String(s).trim()).filter(Boolean).join('\n')
}

function profileToRow(p) {
  const hint = p?.loginHint
  return {
    id: str(p?.id),
    name: str(p?.name),
    pathPrefixText: joinPathLines(p?.match?.pathPrefix),
    headerName: str(p?.header?.name, 'Authorization'),
    valueTemplate: str(p?.header?.valueTemplate),
    loginFlowKey: str(hint?.flowKey),
    loginFrom: str(hint?.from, 'body'),
    loginExpr: str(hint?.expr ?? hint?.extractJsonPath),
  }
}

/**
 * 把库中 authConfig（JSON 字符串或对象）填入表单结构。
 */
export function parseAuthConfig(raw) {
  const form = emptyAuthForm()
  let obj = null
  if (raw == null || raw === '') {
    return form
  }
  if (typeof raw === 'string') {
    try {
      obj = JSON.parse(raw)
    } catch {
      return form
    }
  } else if (typeof raw === 'object') {
    obj = raw
  }
  if (!obj || typeof obj !== 'object') {
    return form
  }
  const profiles = Array.isArray(obj.authProfiles) ? obj.authProfiles.map(profileToRow) : []
  form.profiles = profiles
  form.defaultProfileId = str(obj.defaultProfileId, profiles[0]?.id || '')
  form.anonymousPathExactText = joinPathLines(obj.anonymousPathExact)
  form.anonymousPathPrefixText = joinPathLines(obj.anonymousPathPrefix)
  return form
}

/**
 * 模板对象 → 表单（深拷贝，避免改到 frozen 模板）。
 */
export function applyTemplateToForm(template) {
  return parseAuthConfig(JSON.parse(JSON.stringify(template || RUOYI_BEARER_TEMPLATE)))
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
  }
  if (splitPathLines(form?.anonymousPathPrefixText).some((x) => x === '/')) {
    return 'anonymousPathPrefix 禁止使用 "/"'
  }
  const defaultId = String(form?.defaultProfileId || '').trim()
  if (profiles.length && defaultId && !ids.has(defaultId)) {
    return `defaultProfileId 不在 authProfiles 中: ${defaultId}`
  }
  return null
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
    .map((p) => {
      const id = String(p.id || '').trim()
      const name = String(p.name || '').trim()
      const prefixes = splitPathLines(p.pathPrefixText)
      const headerName = String(p.headerName || '').trim()
      const valueTemplate = String(p.valueTemplate || '').trim()
      const flowKey = String(p.loginFlowKey || '').trim()
      const from = String(p.loginFrom || '').trim()
      const expr = String(p.loginExpr || '').trim()
      const row = {
        id,
        header: { name: headerName, valueTemplate },
      }
      if (name) row.name = name
      if (prefixes.length) {
        row.match = { pathPrefix: prefixes }
      }
      if (flowKey || from || expr) {
        row.loginHint = {}
        if (flowKey) row.loginHint.flowKey = flowKey
        if (from) row.loginHint.from = from
        if (expr) row.loginHint.expr = expr
      }
      return row
    })
    .filter((p) => p.id)

  const exact = splitPathLines(form?.anonymousPathExactText)
  const prefix = splitPathLines(form?.anonymousPathPrefixText)

  if (!profiles.length && !exact.length && !prefix.length) {
    return null
  }

  const payload = {
    authProfiles: profiles,
    anonymousPathExact: exact,
    anonymousPathPrefix: prefix,
  }
  if (profiles.length) {
    payload.defaultProfileId = String(form?.defaultProfileId || '').trim() || profiles[0].id
  }
  return payload
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
