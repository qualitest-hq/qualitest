/**
 * 将模板 templateApis 合成 HttpConfigModal / AI 可用的 API 树与目录。
 * 行上 testProjectApiId 为作者期雪花字符串；缺则 ensure 后生成。
 * 目录由 apiGroup 点号路径嵌套展开（与 Apply 侧 resolve 一致；空 → 默认分组）。
 */
import { resolveApiMethod } from './templateForm'
import { ensureTemplateApiIds, resolveTemplateApiId } from './templateApiId'

/** 与 Apply / TestProjectApiGroupResolveSupport 空路径落点一致 */
export const DEFAULT_API_GROUP = '默认分组'

/**
 * 规范化点号路径：trim、去空段、用 `.` 拼接；空则返回默认分组。
 * @param {unknown} path
 * @returns {string}
 */
export function normalizeApiGroup(path) {
  const raw = String(path ?? '').trim()
  if (!raw) return DEFAULT_API_GROUP
  const segments = raw
    .split('.')
    .map((s) => s.trim())
    .filter(Boolean)
  return segments.length ? segments.join('.') : DEFAULT_API_GROUP
}

/**
 * 从 templateApis 收集去重后的分组路径（已 normalize），供下拉复用。
 * @param {unknown[]} templateApis
 * @returns {string[]}
 */
export function collectApiGroupPaths(templateApis) {
  const seen = new Set()
  const out = []
  const list = Array.isArray(templateApis) ? templateApis : []
  list.forEach((api) => {
    if (!api || typeof api !== 'object') return
    const path = normalizeApiGroup(api.apiGroup)
    if (seen.has(path)) return
    seen.add(path)
    out.push(path)
  })
  return out
}

/**
 * @param {unknown[]} templateApis
 * @returns {{ tree: object[], catalog: Array<{ syntheticId: string, api: object }> }}
 */
export function synthesizeTemplateApiCatalog(templateApis) {
  const { apis: list } = ensureTemplateApiIds(Array.isArray(templateApis) ? templateApis : [])
  const catalog = []
  list.forEach((api, sourceIndex) => {
    if (!api || typeof api !== 'object') return
    const path = String(api.apiPath || '').trim()
    if (!path) return
    const syntheticId = resolveTemplateApiId(api)
    if (!syntheticId) return
    catalog.push({
      syntheticId,
      sourceIndex,
      api: {
        ...api,
        testProjectApiId: syntheticId,
        httpMethod: resolveApiMethod(api),
        apiPath: path,
        apiName: String(api.apiName || '').trim() || path,
        apiGroup: normalizeApiGroup(api.apiGroup),
      },
    })
  })

  /** @type {Map<string, { label: string, groupName: string, groupPath: string, nodeType: string, children: object[], _childGroups: Map<string, object> }>} */
  const rootGroups = new Map()

  function ensureGroup(parentMap, segment, groupPath) {
    if (!parentMap.has(segment)) {
      parentMap.set(segment, {
        label: segment,
        groupName: segment,
        groupPath,
        nodeType: 'group',
        children: [],
        _childGroups: new Map(),
      })
    }
    return parentMap.get(segment)
  }

  catalog.forEach((entry) => {
    const groupPath = normalizeApiGroup(entry.api.apiGroup)
    const segments = groupPath.split('.')
    let map = rootGroups
    let node = null
    let pathSoFar = ''
    for (const segment of segments) {
      pathSoFar = pathSoFar ? `${pathSoFar}.${segment}` : segment
      node = ensureGroup(map, segment, pathSoFar)
      map = node._childGroups
    }
    if (!node) return
    node.children.push({
      nodeType: 'api',
      testProjectApiId: entry.syntheticId,
      sourceIndex: entry.sourceIndex,
      apiName: entry.api.apiName,
      apiPath: entry.api.apiPath,
      httpMethod: entry.api.httpMethod,
      groupPath,
      label: entry.api.apiName,
      treeNodeKey: `a-${entry.syntheticId}`,
    })
  })

  function finalizeGroup(groupNode) {
    const childGroups = [...groupNode._childGroups.values()].map(finalizeGroup)
    const apis = groupNode.children.filter((c) => c.nodeType === 'api')
    const { _childGroups, ...rest } = groupNode
    return {
      ...rest,
      treeNodeKey: `g-${groupNode.groupPath}`,
      children: [...childGroups, ...apis],
    }
  }

  const tree = [...rootGroups.values()].map(finalizeGroup)

  return { tree, catalog }
}

/** 按合成 id 取预制接口详情（工作台形状） */
export function findTemplateApiDetail(catalog, syntheticId) {
  if (!syntheticId) return null
  const id = String(syntheticId)
  const found = (catalog || []).find((e) => String(e.syntheticId) === id)
  return found?.api ?? null
}

/**
 * 判断 api 的 apiGroup 是否等于或落在 filterPath 之下（点号前缀）。
 * @param {unknown} apiGroup
 * @param {string} filterPath normalize 后的路径；空表示不过滤
 */
export function apiGroupMatchesPath(apiGroup, filterPath) {
  const filter = String(filterPath || '').trim()
  if (!filter) return true
  const path = normalizeApiGroup(apiGroup)
  return path === filter || path.startsWith(`${filter}.`)
}
