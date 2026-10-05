/**
 * 调试台托管鉴权头：由接口详情的 managedAuthHeader 生成只读行，并决定发送时带哪些头。
 */

/** 由详情上的托管头生成表格行；没有托管头时返回 null。 */
export function buildManagedRow(managedAuthHeader, enabled = true) {
  if (!managedAuthHeader || !String(managedAuthHeader.name || '').trim()) {
    return null
  }
  return {
    _managed: true,
    _enabled: enabled !== false,
    name: String(managedAuthHeader.name).trim(),
    value: managedAuthHeader.valueTemplate != null ? String(managedAuthHeader.valueTemplate) : '',
    profileName: managedAuthHeader.profileName != null ? String(managedAuthHeader.profileName) : '',
    source: managedAuthHeader.source === 'override' ? 'override' : 'profile',
    type: 'string'
  }
}

/** 托管行的标记文案。 */
export function managedAuthTag(row) {
  if (!row?._managed) return ''
  if (row.source === 'override') return '接口自定义'
  const name = String(row.profileName || '').trim()
  return name ? `项目鉴权 · ${name}` : '项目鉴权'
}

/**
 * 组装实际要发送的请求头。
 * 取消勾选的行不发；同名手动行已启用时，托管行让位。
 */
export function pickHeadersForSend(rows) {
  const list = Array.isArray(rows) ? rows : []
  const manualNames = new Set()
  for (const row of list) {
    if (!row || row._managed || row._enabled === false) continue
    const name = String(row.name || '').trim().toLowerCase()
    if (name) manualNames.add(name)
  }
  const out = {}
  for (const row of list) {
    if (!row || row._enabled === false) continue
    const name = String(row.name || '').trim()
    if (!name) continue
    if (row._managed && manualNames.has(name.toLowerCase())) continue
    out[name] = row.value ?? ''
  }
  return out
}

/** 同名、已启用的手动行会盖过托管行。 */
export function isManagedRowOverridden(rows, row) {
  if (!row?._managed) return false
  const name = String(row.name || '').trim().toLowerCase()
  if (!name) return false
  return (rows || []).some((other) => {
    if (!other || other === row || other._managed || other._enabled === false) return false
    return String(other.name || '').trim().toLowerCase() === name
  })
}
