/**
 * 从 HTTP 节点 extract 行解析将写出的占位路径（asset.entry.field / flow.name / env.name）。
 * 只认明确的 scope / entryKey；不做「含 auth 即 asset」启发式。
 */

export function credentialDisplayPathFromExtractRow(row: Record<string, unknown>): string {
  const scope = row.scope != null ? String(row.scope).trim().toLowerCase() : 'flow'
  let entryKey = row.entryKey != null ? String(row.entryKey).trim() : ''
  let fieldPath = row.fieldPath != null ? String(row.fieldPath).trim() : ''
  const name = row.name != null ? String(row.name).trim() : ''

  // 仅显式 scope=asset 且 name=entry.field、缺 entryKey 时拆分
  if (!entryKey && scope === 'asset' && name.includes('.')) {
    const dot = name.indexOf('.')
    const left = name.slice(0, dot).trim()
    const right = name.slice(dot + 1).trim()
    if (left && right && !name.includes('.', dot + 1)) {
      entryKey = left
      fieldPath = fieldPath || right
    }
  }

  if (scope === 'asset') {
    if (!fieldPath && name) fieldPath = name
    if (!entryKey || !fieldPath) return ''
    return `asset.${entryKey}.${fieldPath}`
  }
  if (scope === 'flow' || scope === 'env' || !scope) {
    const key = scope === 'env' ? 'env' : 'flow'
    return name ? `${key}.${name}` : ''
  }
  return ''
}

/** 列出 extracts 写出的占位路径（去重、保序）。 */
export function listExtractCredentialPaths(extracts: unknown): string[] {
  if (!Array.isArray(extracts)) return []
  const keys: string[] = []
  for (const row of extracts) {
    if (!row || typeof row !== 'object') continue
    const path = credentialDisplayPathFromExtractRow(row as Record<string, unknown>)
    if (path && !keys.includes(path)) keys.push(path)
  }
  return keys
}
