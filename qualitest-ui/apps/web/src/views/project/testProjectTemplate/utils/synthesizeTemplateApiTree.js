/**
 * 将模板 templateApis 合成 HttpConfigModal / AI 可用的 API 树与目录。
 * 合成 id 读行上 testProjectApiId；缺则 ensure 后生成稳定 tpl_*。
 */
import { resolveApiMethod } from './templateForm'
import { ensureTemplateApiIds, resolveTemplateApiId } from './templateApiId'

/**
 * @param {unknown[]} templateApis
 * @returns {{ tree: object[], catalog: Array<{ syntheticId: string, api: object }> }}
 */
export function synthesizeTemplateApiCatalog(templateApis) {
  const { apis: list } = ensureTemplateApiIds(Array.isArray(templateApis) ? templateApis : [])
  const catalog = []
  list.forEach((api) => {
    if (!api || typeof api !== 'object') return
    const path = String(api.apiPath || '').trim()
    if (!path) return
    const syntheticId = resolveTemplateApiId(api)
    if (!syntheticId) return
    catalog.push({
      syntheticId,
      api: {
        ...api,
        testProjectApiId: syntheticId,
        httpMethod: resolveApiMethod(api),
        apiPath: path,
        apiName: String(api.apiName || '').trim() || path,
      },
    })
  })

  const byGroup = new Map()
  catalog.forEach((entry) => {
    const group = String(entry.api.apiGroup || '').trim() || '预制接口'
    if (!byGroup.has(group)) byGroup.set(group, [])
    byGroup.get(group).push(entry)
  })

  const tree = [...byGroup.entries()].map(([groupName, entries]) => ({
    label: groupName,
    groupName,
    nodeType: 'group',
    children: entries.map((entry) => ({
      nodeType: 'api',
      testProjectApiId: entry.syntheticId,
      apiName: entry.api.apiName,
      apiPath: entry.api.apiPath,
      httpMethod: entry.api.httpMethod,
      groupPath: groupName,
      label: entry.api.apiName,
    })),
  }))

  return { tree, catalog }
}

/** 按合成 id 取预制接口详情（工作台形状） */
export function findTemplateApiDetail(catalog, syntheticId) {
  if (!syntheticId) return null
  const id = String(syntheticId)
  const found = (catalog || []).find((e) => String(e.syntheticId) === id)
  return found?.api ?? null
}
