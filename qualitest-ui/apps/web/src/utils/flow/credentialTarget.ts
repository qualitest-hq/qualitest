/**
 * 从 HTTP 节点 extract 行解析凭证展示路径（asset.entry.field / flow.name）。
 * 对齐后端 CredentialTargetSupport.targetFromExtractRow。
 */
export function credentialDisplayPathFromExtractRow(row: Record<string, unknown>): string {
  const scope = row.scope != null ? String(row.scope).trim().toLowerCase() : 'flow'
  if (scope === 'asset') {
    const entryKey = row.entryKey != null ? String(row.entryKey).trim() : ''
    let fieldPath = row.fieldPath != null ? String(row.fieldPath).trim() : ''
    if (!fieldPath && row.name != null) fieldPath = String(row.name).trim()
    if (!entryKey || !fieldPath) return ''
    return `asset.${entryKey}.${fieldPath}`
  }
  if (scope === 'flow' || !scope) {
    const name = row.name != null ? String(row.name).trim() : ''
    return name ? `flow.${name}` : ''
  }
  return ''
}

/** 列出 extracts 产出的凭证展示路径（去重、保序）。 */
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
