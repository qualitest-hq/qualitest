/**
 * 模板预制流画布灌入：从 catalog 同步 HTTP 合成绑定、探活再登录标准排版。
 */
import { parseJsonMaybe } from '../../testProject/utils/projectAuthConfig'
import { updateSummary } from '../../testFlow/utils/nodeDataUtils'

/**
 * 探活再登录骨架坐标。
 * 上排：探活 → 有效性（无出边结束）；左侧起点做凭证判断。
 * 下排：登录居中汇合两条 ELSE。
 */
export const LOGIN_FLOW_NODE_LAYOUT = {
  cond_token: { x: 80, y: 260 },
  probe_http: { x: 480, y: 60 },
  cond_alive: { x: 960, y: 60 },
  login_http: { x: 680, y: 440 },
}

/** 探活再登录骨架打开时的默认视口（略缩小以容纳整图） */
export const LOGIN_FLOW_VIEWPORT = { x: 24, y: 16, zoom: 0.82 }

/** 探活再登录骨架默认边（凭证 IF→探活、ELSE→登录；探活→有效性；有效性 ELSE→登录） */
export const LOGIN_FLOW_EDGES = [
  { id: 'e_token_if', source: 'cond_token', target: 'probe_http' },
  { id: 'e_token_else', source: 'cond_token', target: 'login_http' },
  { id: 'e_probe', source: 'probe_http', target: 'cond_alive' },
  { id: 'e_alive_else', source: 'cond_alive', target: 'login_http' },
]

/** 骨架图 edges 被清空时（草稿损坏）恢复默认拓扑 */
export function recoverLoginFlowEdgesIfMissing(nodes, edges) {
  if (Array.isArray(edges) && edges.length > 0) return edges
  if (!isLoginFlowSkeleton(nodes)) return edges ?? []
  return LOGIN_FLOW_EDGES.map((e) => ({ ...e }))
}

export function isLoginFlowSkeleton(nodes) {
  const ids = new Set((nodes || []).map((n) => n?.id))
  return ids.has('cond_token') && ids.has('probe_http') && ids.has('login_http')
}

function resolveCatalogMethod(api) {
  if (!api || typeof api !== 'object') return 'GET'
  const cfg = api.requestConfig
  if (cfg && typeof cfg === 'object' && cfg.method) {
    return String(cfg.method).trim().toUpperCase()
  }
  return String(api.httpMethod || 'GET').trim().toUpperCase()
}

function catalogIndex(catalog) {
  const byId = new Map()
  const byIdentity = new Map()
  ;(catalog || []).forEach((entry) => {
    const api = entry?.api
    if (!api || typeof api !== 'object') return
    const syntheticId = String(entry.syntheticId ?? api.testProjectApiId ?? '').trim()
    if (syntheticId) byId.set(syntheticId, api)
    const method = String(api.httpMethod || resolveCatalogMethod(api)).trim().toUpperCase()
    const path = String(api.apiPath || '').trim()
    if (path) byIdentity.set(`${method} ${path}`, api)
  })
  return { byId, byIdentity }
}

/**
 * 将 store 内 HTTP 节点与 templateApis catalog 对齐（补合成 id / apiName / summary）。
 * 仅有 method+path 时按 catalog 回填合成 id（作者期合法绑定）。
 * @returns 是否有节点被改写
 */
export function syncTemplateHttpNodesFromCatalog(nodes, catalog) {
  const { byId, byIdentity } = catalogIndex(catalog)
  if (!byId.size && !byIdentity.size) return false

  let changed = false
  ;(nodes || []).forEach((node) => {
    if (!node || String(node.type || '').toLowerCase() !== 'http') return
    const data = node.data && typeof node.data === 'object' ? node.data : {}
    if (String(data.callMode || 'project').toLowerCase() !== 'project') return

    let api = null
    const rawId = String(data.testProjectApiId ?? '').trim()
    if (rawId && byId.has(rawId)) {
      api = byId.get(rawId)
    } else {
      const method = String(data.httpMethod || 'GET').trim().toUpperCase()
      const path = String(data.apiPath || '').trim()
      if (path) api = byIdentity.get(`${method} ${path}`) ?? null
    }
    if (!api) return

    let nodeChanged = false
    const syntheticId = String(api.testProjectApiId ?? rawId).trim()
    if (syntheticId && data.testProjectApiId !== syntheticId) {
      data.testProjectApiId = syntheticId
      nodeChanged = true
    }
    const apiName = String(api.apiName || data.apiName || '').trim()
    if (apiName && data.apiName !== apiName) {
      data.apiName = apiName
      nodeChanged = true
    }
    const method = String(api.httpMethod || resolveCatalogMethod(api) || data.httpMethod || 'GET')
      .trim()
      .toUpperCase()
    if (data.httpMethod !== method) {
      data.httpMethod = method
      nodeChanged = true
    }
    if (nodeChanged) {
      updateSummary('http', data)
      node.data = data
      changed = true
    }
  })
  return changed
}

/**
 * 识别探活再登录骨架时应用标准排版（仅改 position）。
 * @returns 是否调整过坐标
 */
export function applyLoginFlowLayoutIfPresent(nodes) {
  let changed = false
  ;(nodes || []).forEach((node) => {
    const pos = LOGIN_FLOW_NODE_LAYOUT[node?.id]
    if (!pos) return
    if (!node.position) {
      node.position = { ...pos }
      changed = true
      return
    }
    if (node.position.x !== pos.x || node.position.y !== pos.y) {
      node.position = { ...pos }
      changed = true
    }
  })
  return changed
}

/** 对 graph 对象做绑定同步、排版 */
export function hydrateTemplateFlowGraph(graph, catalog) {
  const nodes = graph?.nodes
  if (!Array.isArray(nodes)) return false
  let changed = syncTemplateHttpNodesFromCatalog(nodes, catalog)
  changed = applyLoginFlowLayoutIfPresent(nodes) || changed
  return changed
}

/**
 * 模板表单内每条预制流的 graphJson 与 catalog 对齐（打开抽屉 / 写草稿前调用）。
 * @returns {{ flows: object[], changed: boolean }}
 */
export function hydrateTemplateFlowsGraphs(templateFlows, catalog) {
  const list = Array.isArray(templateFlows) ? templateFlows : []
  let changed = false
  const flows = list.map((flow) => {
    if (!flow || typeof flow !== 'object') return flow
    const raw = flow.graphJson
    const graph =
      raw && typeof raw === 'object' && !Array.isArray(raw)
        ? JSON.parse(JSON.stringify(raw))
        : parseJsonMaybe(raw)
    if (!graph || !Array.isArray(graph.nodes)) return flow
    const patched = hydrateTemplateFlowGraph(graph, catalog)
    if (!patched) return flow
    changed = true
    return { ...flow, graphJson: graph }
  })
  return { flows, changed }
}
