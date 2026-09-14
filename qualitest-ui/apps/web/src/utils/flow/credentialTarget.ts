/**
 * 从 HTTP 节点 extract 行解析凭证展示路径（asset.entry.field / flow.name）。
 *
 * 缺 entryKey 且 name 含点号时：若 scope 已是 asset，或未写 scope 且 name 左侧含 auth，
 * 则把 name 拆成 entryKey + fieldPath，并按 asset 作用域拼出展示路径。
 * asset 作用域输出 `asset.{entryKey}.{fieldPath}`；flow（或缺省）输出 `flow.{name}`。
 */
export function credentialDisplayPathFromExtractRow(row: Record<string, unknown>): string {
  let scope = row.scope != null ? String(row.scope).trim().toLowerCase() : 'flow'
  let entryKey = row.entryKey != null ? String(row.entryKey).trim() : ''
  let fieldPath = row.fieldPath != null ? String(row.fieldPath).trim() : ''
  const name = row.name != null ? String(row.name).trim() : ''

  if (!entryKey && name.includes('.')) {
    const dot = name.indexOf('.')
    const left = name.slice(0, dot).trim()
    const right = name.slice(dot + 1).trim()
    const explicitAsset = scope === 'asset'
    const omittedScope = row.scope == null || String(row.scope).trim() === ''
    if (left && right && (explicitAsset || (omittedScope && left.toLowerCase().includes('auth')))) {
      entryKey = left
      fieldPath = fieldPath || right
      scope = 'asset'
    }
  }

  if (scope === 'asset') {
    if (!fieldPath && name) fieldPath = name
    if (!entryKey || !fieldPath) return ''
    return `asset.${entryKey}.${fieldPath}`
  }
  if (scope === 'flow' || !scope) {
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
