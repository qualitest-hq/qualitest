/**
 * 子流入口落盘读写兼容。
 * - 旧图若仍用三节 inputShape，展平为扁平 inputDecl 供面板编辑与保存。
 * - 旧图 fallbackOut 若是完整 status/headers/body，编辑时只取出 body 业务对象；
 *   保存时落盘为业务对象，引擎收束时再包成 out.body。
 */

import { defaultSubflowFallbackOut } from '@/utils/flow/subflowEntryDefaults'

type Dict = Record<string, unknown>

/** 把未知值收成字段对象数组，非数组或非对象元素忽略 */
function asFieldList(raw: unknown): Dict[] {
  if (!Array.isArray(raw)) return []
  return raw.filter((x) => x && typeof x === 'object') as Dict[]
}

/**
 * 读取子流入口入参声明。
 * 已有 inputDecl 直接返回；否则把旧 inputShape 的 body/query/headers 按出现顺序展平，
 * 同名键去重，得到 [{ name, type, required }, ...]。
 * 无任何声明时返回空数组；data 无效时返回 undefined。
 */
export function migrateSubflowInputDecl(data: Dict | null | undefined): Dict[] | undefined {
  if (!data || typeof data !== 'object') return undefined
  if (Array.isArray(data.inputDecl)) {
    return data.inputDecl as Dict[]
  }
  const shape = data.inputShape
  if (!shape || typeof shape !== 'object') return undefined
  const s = shape as Dict
  const out: Dict[] = []
  const seen = new Set<string>()
  for (const key of ['body', 'query', 'headers'] as const) {
    for (const row of asFieldList(s[key])) {
      const name = String(row.name ?? '').trim()
      if (!name || seen.has(name)) continue
      seen.add(name)
      out.push({
        name,
        type: row.type ?? 'string',
        required: !!row.required,
      })
    }
  }
  return out.length ? out : []
}

/**
 * 子流 fallbackOut 编辑态取值。
 * - 已是业务对象：原样拷贝。
 * - 完整出参形（含 body 对象）：只编辑 body。
 * - 仅有 status/headers 无可用 body：回退默认业务对象。
 * - 非法值：回退默认业务对象。
 */
export function subflowFallbackOutForEdit(raw: unknown): Dict {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return defaultSubflowFallbackOut()
  }
  const m = raw as Dict
  if (m.body != null && typeof m.body === 'object' && !Array.isArray(m.body)) {
    return { ...(m.body as Dict) }
  }
  if ('status' in m || 'headers' in m) {
    return defaultSubflowFallbackOut()
  }
  return { ...m }
}
