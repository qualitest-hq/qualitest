/**
 * 鉴权头行：托管标记与 {{flow.*}} 占位解析。
 * 画布凭证作用域、Staging 黄条、Headers 表共用。
 */

const FLOW_PLACEHOLDER = /\{\{\s*flow\.([A-Za-z0-9_]+)\s*\}\}/

/** 是否为项目鉴权自动写入的托管头行 */
export function isProfileManagedRow(row: Record<string, unknown> | null | undefined): boolean {
  if (!row || typeof row !== 'object') return false
  const managed = row.profileManaged
  return managed === true || managed === 'true'
}

/** 从头值模板解析第一个 flow 变量名，如 Bearer {{flow.adminToken}} → adminToken */
export function parseFlowPlaceholderKey(value: unknown): string {
  const m = String(value || '').match(FLOW_PLACEHOLDER)
  return m?.[1] || ''
}
