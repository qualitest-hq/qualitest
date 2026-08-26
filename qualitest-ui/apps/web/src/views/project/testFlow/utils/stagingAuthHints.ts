/**
 * Staging 鉴权相关的纯展示工具：识别托管头、解析机器码、剥 code 前缀。
 */

import { isProfileManagedRow, parseCredentialDisplayPath } from './authHeaderRow'

/** 鉴权提示/错误机器码常量 */
export const AUTH_WARNING_CODES = {
  /** 已自动补上托管鉴权头，仅提示 */
  HEADER_MANAGED: 'AUTH_HEADER_MANAGED',
  /** 登录/免登口已剥离误补托管头，仅提示 */
  LOGIN_NO_BEARER: 'AUTH_LOGIN_NO_BEARER',
  /** 登录口缺少 token extract，应硬拦 */
  LOGIN_EXTRACT_MISSING: 'AUTH_LOGIN_EXTRACT_MISSING',
  /** 两套不同登录口抽出同一个 flow 变量，应硬拦 */
  LOGIN_FLOWKEY_COLLISION: 'AUTH_LOGIN_FLOWKEY_COLLISION',
  /** 缺对应端 token 来源，应硬拦（出现在 errors） */
  TOKEN_MISSING: 'AUTH_TOKEN_MISSING',
} as const

const AUTH_WARNING_CODE_SET = new Set<string>(Object.values(AUTH_WARNING_CODES))

/** 取草稿节点 data.headers 数组 */
function draftHeaders(draft?: Record<string, unknown>): Record<string, unknown>[] {
  const data = draft?.data as Record<string, unknown> | undefined
  return Array.isArray(data?.headers) ? (data!.headers as Record<string, unknown>[]) : []
}

/** 列出草稿中的托管鉴权头行 */
export function listProfileManagedHeaders(draft?: Record<string, unknown>): Record<string, unknown>[] {
  return draftHeaders(draft).filter(isProfileManagedRow)
}

/** 草稿是否含托管鉴权头 */
export function hasProfileManagedHeaders(draft?: Record<string, unknown>): boolean {
  return listProfileManagedHeaders(draft).length > 0
}

/**
 * 根据草稿托管头生成短提示文案。
 * 用于校验结果里没有 AUTH_HEADER_MANAGED 时，仍能在 Diff 旁提示「已按项目鉴权补全」。
 */
export function collectAuthManagedHeaderHints(draft?: Record<string, unknown>): string[] {
  const managed = listProfileManagedHeaders(draft)
  if (!managed.length) return []
  const names = managed
    .map((row) => String(row.name || row.key || 'Authorization').trim())
    .filter(Boolean)
  const unique = [...new Set(names)]
  const credentialPaths = [...new Set(
    managed
      .map((row) => parseCredentialDisplayPath(row.value))
      .filter(Boolean),
  )]
  const keyHint = credentialPaths.length ? `，凭证 ${credentialPaths.join('、')}` : ''
  return [`已按项目鉴权补全 ${unique.join('、')}（托管头，Run 时随项目配置刷新${keyHint}）`]
}

/** Diff 里 headers 字段的展示标签：有托管头时标明「按项目鉴权补全」 */
export function stagingHeadersFieldLabel(draft?: Record<string, unknown>): string {
  return hasProfileManagedHeaders(draft) ? '请求头（按项目鉴权补全）' : '请求头'
}

/**
 * 解析「CODE: 文案」形式的鉴权机器码字符串。
 * 无法识别或格式不对时返回 null。
 */
export function parseAuthWarning(raw: unknown): { code: string; message: string } | null {
  if (typeof raw !== 'string' || !raw.trim()) return null
  const sep = raw.indexOf(': ')
  if (sep <= 0) return null
  const code = raw.slice(0, sep).trim()
  if (!AUTH_WARNING_CODE_SET.has(code)) return null
  const message = raw.slice(sep + 2).trim()
  return message ? { code, message } : null
}

/**
 * 展示用文案：若是鉴权机器码则去掉 CODE 前缀，只留人类可读部分；否则原样 trim。
 * 用于 Staging 确认失败时的错误列表展示。
 */
export function displayAuthCodedMessage(raw: unknown): string {
  if (typeof raw !== 'string') return ''
  const parsed = parseAuthWarning(raw)
  return parsed ? parsed.message : raw.trim()
}

/**
 * 从校验 warnings 中筛出鉴权托管相关提示（补头 / 登录口剥离），并去掉 CODE 前缀。
 */
export function filterAuthRelatedWarnings(warnings: unknown[] | undefined | null): string[] {
  if (!Array.isArray(warnings)) return []
  const out: string[] = []
  for (const w of warnings) {
    const parsed = parseAuthWarning(w)
    if (
      parsed?.code === AUTH_WARNING_CODES.HEADER_MANAGED
      || parsed?.code === AUTH_WARNING_CODES.LOGIN_NO_BEARER
    ) {
      out.push(parsed.message)
    }
  }
  return out
}
