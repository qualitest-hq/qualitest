/**
 * Staging 鉴权托管头可见提示（纯函数）。
 */

/** 与后端 {@code AuthDesignWarningCodes} 对齐的稳定机器码 */
export const AUTH_WARNING_CODES = {
  HEADER_MANAGED: 'AUTH_HEADER_MANAGED',
  TOKEN_MISSING: 'AUTH_TOKEN_MISSING',
} as const

const AUTH_WARNING_CODE_SET = new Set<string>(Object.values(AUTH_WARNING_CODES))

function draftHeaders(draft?: Record<string, unknown>): Record<string, unknown>[] {
  const data = draft?.data as Record<string, unknown> | undefined
  return Array.isArray(data?.headers) ? (data!.headers as Record<string, unknown>[]) : []
}

function isProfileManagedRow(row: Record<string, unknown> | null | undefined): boolean {
  if (!row || typeof row !== 'object') return false
  const managed = row.profileManaged
  return managed === true || managed === 'true'
}

/** 草稿中 profileManaged 托管头行 */
export function listProfileManagedHeaders(draft?: Record<string, unknown>): Record<string, unknown>[] {
  return draftHeaders(draft).filter(isProfileManagedRow)
}

/** 草稿节点 headers 中是否含 profileManaged 托管行 */
export function hasProfileManagedHeaders(draft?: Record<string, unknown>): boolean {
  return listProfileManagedHeaders(draft).length > 0
}

/** 从草稿托管头生成短提示文案（无 validation.warnings 时兜底） */
export function collectAuthManagedHeaderHints(draft?: Record<string, unknown>): string[] {
  const managed = listProfileManagedHeaders(draft)
  if (!managed.length) return []
  const names = managed
    .map((row) => String(row.name || row.key || 'Authorization').trim())
    .filter(Boolean)
  const unique = [...new Set(names)]
  if (!unique.length) {
    return ['已按项目鉴权补全托管头（Run 时随项目配置刷新）']
  }
  return [`已按项目鉴权补全 ${unique.join('、')}（托管头，Run 时随项目配置刷新）`]
}

/** headers 字段在 Diff 中的展示标签 */
export function stagingHeadersFieldLabel(draft?: Record<string, unknown>): string {
  return hasProfileManagedHeaders(draft) ? '请求头（按项目鉴权补全）' : '请求头'
}

/**
 * 解析后端 warning：`CODE: 文案`。
 * 非鉴权 code 返回 null。
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

/** 造流 / Staging 校验 warnings 中与鉴权相关的条目（展示用人类文案） */
export function filterAuthRelatedWarnings(warnings: unknown[] | undefined | null): string[] {
  if (!Array.isArray(warnings)) return []
  const out: string[] = []
  for (const w of warnings) {
    const parsed = parseAuthWarning(w)
    if (parsed) out.push(parsed.message)
  }
  return out
}
