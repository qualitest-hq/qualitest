/**
 * 模板预制 API 稳定合成 id：读行上已有 id，缺则生成 tpl_<随机>。
 */

/** 新预制接口默认合成 id */
export function newTemplateApiId() {
  const rand = Math.random().toString(36).slice(2, 10)
  return `tpl_${Date.now().toString(36)}_${rand}`
}

/** 读行上 testProjectApiId；无则空串（调用方应先 ensureTemplateApiIds）。 */
export function resolveTemplateApiId(api) {
  if (api && typeof api === 'object') {
    const existing = String(api.testProjectApiId ?? '').trim()
    if (existing) return existing
  }
  return ''
}

/**
 * 保证 templateApis 每行都有稳定 testProjectApiId；返回新数组，必要时写回 id。
 * @param {unknown[]} templateApis
 * @returns {{ apis: object[], changed: boolean }}
 */
export function ensureTemplateApiIds(templateApis) {
  const list = Array.isArray(templateApis) ? templateApis : []
  let changed = false
  const apis = list.map((api) => {
    if (!api || typeof api !== 'object') return api
    const existing = String(api.testProjectApiId ?? '').trim()
    if (existing) {
      return api
    }
    changed = true
    return { ...api, testProjectApiId: newTemplateApiId() }
  })
  return { apis, changed }
}
