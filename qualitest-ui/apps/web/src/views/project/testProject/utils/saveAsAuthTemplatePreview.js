/**
 * 另存项目模板对话框：本地预览用的流图 / 素材引用解析（与后端规则对齐，不写库）。
 */

const ASSET_KEY_RE = /\{\{\s*asset\.([a-zA-Z0-9_]+)/g

/** 从文本中收集 {{asset.key...}} 的入口 key */
export function collectAssetKeysFromText(text, into = new Set()) {
  if (!text) return into
  const src = typeof text === 'string' ? text : JSON.stringify(text)
  ASSET_KEY_RE.lastIndex = 0
  let m
  while ((m = ASSET_KEY_RE.exec(src)) !== null) {
    if (m[1]) into.add(m[1])
  }
  return into
}

/** 收集 callMode=project 的 HTTP 节点 testProjectApiId（去重保序） */
export function collectGraphHttpApiIds(graphJson) {
  const ids = []
  if (!graphJson) return ids
  try {
    const graph = typeof graphJson === 'string' ? JSON.parse(graphJson) : graphJson
    for (const node of graph?.nodes || []) {
      if (String(node?.type || '').toLowerCase() !== 'http') continue
      const data = node.data || {}
      const mode = String(data.callMode || 'project').toLowerCase()
      if (mode !== 'project') continue
      const id = data.testProjectApiId != null ? String(data.testProjectApiId).trim() : ''
      if (id && !ids.includes(id)) ids.push(id)
    }
  } catch {
    /* ignore */
  }
  return ids
}

export function apiMethodOf(api) {
  try {
    const cfg = typeof api?.requestConfig === 'string' ? JSON.parse(api.requestConfig) : api?.requestConfig
    return String(cfg?.method || 'GET').trim().toUpperCase() || 'GET'
  } catch {
    return 'GET'
  }
}

export function apiIdentityOf(api) {
  return `${apiMethodOf(api)} ${String(api?.apiPath || '').trim()}`
}

/** Profile 表单行 / 预制口 → METHOD path */
export function prefabIdentityOf(prefab) {
  const method = String(prefab?.method || prefab?.requestConfig?.method || 'GET')
    .trim()
    .toUpperCase() || 'GET'
  return `${method} ${String(prefab?.apiPath || '').trim()}`
}

export function apiLabelOf(api) {
  const method = apiMethodOf(api)
  const path = api?.apiPath || ''
  const name = api?.apiName ? ` ${api.apiName}` : ''
  return `${method} ${path}${name}`
}
