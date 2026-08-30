/**
 * 模板预制 API 作者期 id：与正式接口同形的雪花十进制字符串；Apply 时 remap 为项目主键。
 */

import { nextSnowflakeId } from '@/utils/flow/snowflakeId'

/** 新预制接口默认雪花 id（字符串） */
export function newTemplateApiId() {
  return nextSnowflakeId()
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
