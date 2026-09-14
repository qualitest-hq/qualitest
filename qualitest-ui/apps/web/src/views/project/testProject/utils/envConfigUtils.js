import { parseVariableEntries } from './variableEntryUtils'

/** 默认模块名称，与 Apifox 类工具习惯一致 */
export const DEFAULT_ENV_MODULE_NAME = '默认模块'

/**
 * 从存储的 envUrl 解析出「模块 → 前置 URL」行列表
 * @param {string|null|undefined} raw
 * @returns {{ moduleName: string, url: string }[]}
 */
export function parseEnvUrlRows(raw) {
  const s = (raw || '').trim()
  if (!s) {
    return [{ moduleName: DEFAULT_ENV_MODULE_NAME, url: '' }]
  }
  if (s.startsWith('{')) {
    try {
      const o = JSON.parse(s)
      if (o && typeof o === 'object' && !Array.isArray(o)) {
        const keys = Object.keys(o)
        if (keys.length) {
          return keys.map((k) => ({
            moduleName: k,
            url: String(o[k] ?? '')
          }))
        }
      }
    } catch {
      /* 回落为整串作为默认模块 URL */
    }
  }
  return [{ moduleName: DEFAULT_ENV_MODULE_NAME, url: s }]
}

/**
 * 将模块行保存为 envUrl 字符串（兼容仅「默认模块」单行 → 存纯 URL）
 * @param {{ moduleName: string, url: string }[]} rows
 */
export function serializeEnvUrlRows(rows) {
  const cleaned = (rows || [])
    .map((r) => ({
      moduleName: (r.moduleName || '').trim() || DEFAULT_ENV_MODULE_NAME,
      url: (r.url || '').trim()
    }))
    .filter((r) => r.moduleName || r.url)

  if (!cleaned.length) {
    return ''
  }
  const map = {}
  for (const r of cleaned) {
    map[r.moduleName] = r.url
  }
  const keys = Object.keys(map)
  if (keys.length === 1 && keys[0] === DEFAULT_ENV_MODULE_NAME) {
    return map[DEFAULT_ENV_MODULE_NAME]
  }
  return JSON.stringify(map)
}

/**
 * 调试请求使用的 Base URL：支持纯字符串与多模块 JSON 对象两种存储
 */
export function resolveEnvBaseUrlForRequest(envUrl) {
  const raw = (envUrl || '').trim()
  if (!raw) return ''
  if (raw.startsWith('{')) {
    try {
      const o = JSON.parse(raw)
      if (o && typeof o === 'object' && !Array.isArray(o)) {
        const preferred = o[DEFAULT_ENV_MODULE_NAME]
        if (typeof preferred === 'string' && preferred.trim()) {
          return preferred.trim()
        }
        const first = Object.values(o).find((v) => typeof v === 'string' && v.trim())
        return first ? first.trim() : ''
      }
    } catch {
      return ''
    }
  }
  return raw
}

/** 调试/转发请求用：无协议时补 http://，避免 URI 解析失败 */
export function ensureHttpSchemeForRequest(baseUrl) {
  const u = (baseUrl || '').trim()
  if (!u) return ''
  if (/^https?:\/\//i.test(u)) return u
  return `http://${u}`
}

/** 被测系统快照服务挂载路径，与后端 ResetEndpointSupport.TEST_SUPPORT_PATH 一致 */
export const TEST_SUPPORT_PATH = '/test-support'

/**
 * 从 envUrl 派生 test-support 根地址（静态，不发 HTTP）。
 * @param {string|null|undefined} envUrl
 * @returns {string} 如 http://host:8801/test-support；无法派生时返回 ''
 */
export function resolveResetBaseUrl(envUrl) {
  const base = ensureHttpSchemeForRequest(resolveEnvBaseUrlForRequest(envUrl))
  if (!base) return ''
  let trimmed = base
  while (trimmed.endsWith('/')) {
    trimmed = trimmed.slice(0, -1)
  }
  const path = TEST_SUPPORT_PATH.startsWith('/') ? TEST_SUPPORT_PATH : `/${TEST_SUPPORT_PATH}`
  return `${trimmed}${path}`
}

/**
 * 前置 URL 格式校验：不强制 http(s) 前缀，允许无协议（如 localhost:8800）、占位符、相对路径等。
 * 始终视为可接受，具体拼接与合法性由调试请求等环节处理。
 */
export function isLikelyValidHttpUrl() {
  return true
}

/** 列表页「环境 URL」列简短展示 */
export function previewEnvUrlLabel(envUrl) {
  const base = resolveEnvBaseUrlForRequest(envUrl)
  if (!base) {
    const rows = parseEnvUrlRows(envUrl)
    const first = rows[0]
    return (first?.url || '').trim() || '—'
  }
  return base.length > 48 ? `${base.slice(0, 45)}…` : base
}

/** 列表页「环境变量」列简短展示 */
export function previewEnvVariablesLabel(raw) {
  const entries = parseVariableEntries(raw)
  const keyed = entries.filter((e) => (e.key || '').trim())
  if (!keyed.length) return '—'
  const preview = keyed.slice(0, 2).map((e) => e.key.trim())
  const more = keyed.length > 2 ? ` 等${keyed.length}项` : ''
  return `${preview.join('、')}${more}`
}

/** 与环境管理侧栏一致：无自定义色时按名称哈希取色 */
const ENV_NAME_BADGE_PALETTE = ['#7c3aed', '#2563eb', '#db2777', '#059669', '#d97706']

export function badgeColorForEnvName(name) {
  const n = (name || '').trim()
  if (!n) return ENV_NAME_BADGE_PALETTE[0]
  let h = 0
  for (let i = 0; i < n.length; i++) {
    h = (h + n.charCodeAt(i) * (i + 1)) % ENV_NAME_BADGE_PALETTE.length
  }
  return ENV_NAME_BADGE_PALETTE[h]
}

/**
 * 环境色块背景：优先 `env.envColor`，否则按 `env.envName` 生成稳定配色（与环境管理一致）。
 * @param {{ envColor?: string, envName?: string }|null|undefined} env
 */
export function resolveEnvSwatchBackground(env) {
  const c = (env?.envColor || '').trim()
  if (c) return c
  return badgeColorForEnvName(env?.envName)
}

/** 环境色在界面上的铺色浓度（其余为白），全局一致略偏淡 */
const ENV_UI_SURFACE_ACCENT_RATIO = 0.24

/**
 * 将保存/解析得到的强调色与白混合，用于侧栏、色盘、工具栏等背景（不改变入库色值）。
 * @param {string} accentHex
 */
export function softenEnvUiSurface(accentHex) {
  const rgb = parseHexRgb(accentHex)
  if (!rgb) return '#ffffff'
  const a = ENV_UI_SURFACE_ACCENT_RATIO
  const w = 1 - a
  const blend = (x) => Math.round(x * a + 255 * w)
  const h = (n) => n.toString(16).padStart(2, '0')
  return `#${h(blend(rgb.r))}${h(blend(rgb.g))}${h(blend(rgb.b))}`
}

function parseHexRgb(hex) {
  const s = (hex || '').trim().replace(/^#/, '')
  if (!s) return null
  if (s.length === 3) {
    return {
      r: parseInt(s[0] + s[0], 16),
      g: parseInt(s[1] + s[1], 16),
      b: parseInt(s[2] + s[2], 16)
    }
  }
  if (s.length === 6) {
    return {
      r: parseInt(s.slice(0, 2), 16),
      g: parseInt(s.slice(2, 4), 16),
      b: parseInt(s.slice(4, 6), 16)
    }
  }
  return null
}

/**
 * 纯色背景上的前景色（环境标签等），保证可读性。
 * @param {string} backgroundHex 如 #RRGGBB / #RGB
 */
export function pickContrastForegroundForBg(backgroundHex) {
  const rgb = parseHexRgb(backgroundHex)
  if (!rgb) return '#0f172a'
  const lin = (c) => {
    const x = c / 255
    return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4)
  }
  const L = 0.2126 * lin(rgb.r) + 0.7152 * lin(rgb.g) + 0.0722 * lin(rgb.b)
  return L > 0.55 ? '#0f172a' : '#ffffff'
}
