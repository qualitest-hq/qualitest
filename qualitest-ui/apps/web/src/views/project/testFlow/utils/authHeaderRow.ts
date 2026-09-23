/**
 * 鉴权头行：托管标记与 {{asset.*}} / {{flow.*}} 凭证占位解析。
 * 画布凭证作用域、Staging 黄条、Headers 表共用。
 */
import { listMustacheInners } from '@/utils/flow/mustacheScan'

/** 头值模板中的一条凭证占位：flow 变量或 asset.入口.字段 */
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

/**
 * 按 scope 与点号后路径组装凭证占位。
 * flow：取第一段为变量名；asset：第一段为入口名，其余为字段路径。
 */
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
 * 从头值模板解析全部凭证占位（保序去重）。
 * 只认内层为 flow.… 或 asset.… 的占位；其它花括号忽略。
 */
export function parseCredentialPlaceholders(value: unknown): CredentialPlaceholder[] {
  const text = String(value || '')
  const out: CredentialPlaceholder[] = []
  const seen = new Set<string>()
  for (const inner of listMustacheInners(text)) {
    const dot = inner.indexOf('.')
    if (dot <= 0 || dot >= inner.length - 1) continue
    const scope = inner.slice(0, dot).trim().toLowerCase()
    if (scope !== 'flow' && scope !== 'asset') continue
    const target = parseRest(scope, inner.slice(dot + 1))
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
