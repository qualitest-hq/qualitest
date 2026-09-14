/**
 * 鉴权头行：托管标记与 {{asset.*}} / {{flow.*}} 凭证占位解析。
 * 画布凭证作用域、Staging 黄条、Headers 表共用。
 */

const CREDENTIAL_PLACEHOLDER = /\{\{\s*(asset|flow)\.([^}]+?)\s*\}\}/gi

/** 头值模板中的一条凭证占位（对齐后端 CredentialTargetSupport） */
export interface CredentialPlaceholder {
  scope: 'flow' | 'asset'
  /** 展示路径，如 flow.token / asset.adminAuth.token */
  displayPath: string
  flowKey?: string
  entryKey?: string
  fieldPath?: string
}

/** 是否为项目鉴权自动写入的托管头行 */
export function isProfileManagedRow(row: Record<string, unknown> | null | undefined): boolean {
  if (!row || typeof row !== 'object') return false
  const managed = row.profileManaged
  return managed === true || managed === 'true'
}

function parseRest(scope: 'flow' | 'asset', rest: string): CredentialPlaceholder | null {
  const trimmed = String(rest || '').trim()
  if (!trimmed) return null
  if (scope === 'flow') {
    const flowKey = trimmed.includes('.') ? trimmed.slice(0, trimmed.indexOf('.')) : trimmed
    if (!flowKey) return null
    return { scope: 'flow', displayPath: `flow.${flowKey}`, flowKey }
  }
  const dot = trimmed.indexOf('.')
  if (dot <= 0 || dot >= trimmed.length - 1) return null
  const entryKey = trimmed.slice(0, dot).trim()
  const fieldPath = trimmed.slice(dot + 1).trim()
  if (!entryKey || !fieldPath) return null
  return {
    scope: 'asset',
    displayPath: `asset.${entryKey}.${fieldPath}`,
    entryKey,
    fieldPath,
  }
}

/**
 * 从头值模板解析全部凭证占位符（保序去重）
 */
export function parseCredentialPlaceholders(value: unknown): CredentialPlaceholder[] {
  const text = String(value || '')
  const out: CredentialPlaceholder[] = []
  const seen = new Set<string>()
  const re = new RegExp(CREDENTIAL_PLACEHOLDER.source, CREDENTIAL_PLACEHOLDER.flags)
  let m: RegExpExecArray | null
  while ((m = re.exec(text)) !== null) {
    const scope = m[1].trim().toLowerCase() as 'flow' | 'asset'
    const target = parseRest(scope, m[2])
    if (!target) continue
    const key = target.displayPath.toLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    out.push(target)
  }
  return out
}

/** 取头值模板中的第一条凭证占位 */
export function parsePrimaryCredentialPlaceholder(value: unknown): CredentialPlaceholder | null {
  const list = parseCredentialPlaceholders(value)
  return list[0] || null
}

/** 从头值模板解析展示路径（flow.x / asset.a.b） */
export function parseCredentialDisplayPath(value: unknown): string {
  return parsePrimaryCredentialPlaceholder(value)?.displayPath || ''
}
