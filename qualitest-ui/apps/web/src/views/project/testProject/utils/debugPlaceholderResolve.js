import { listMustacheInners } from '@/utils/flow/mustacheScan'
import { resolvePathSegment, resolvePlaceholder } from '@/utils/flow/placeholder'

/**
 * 对单个字符串做 {{scope.path}} 占位符替换（lenient：未定义为空串）。
 * 用于发送请求前解析 path、header、body 等字段。
 */
export function resolvePlaceholderInString(value, ctx) {
  if (value == null) return value
  if (typeof value !== 'string') return value
  return resolvePlaceholder(value, ctx)
}

/** 解析 KV 表每行的 name、value */
export function resolveKvRows(rows, ctx) {
  return (rows || []).map((row) => ({
    ...row,
    name: resolvePlaceholderInString(row.name, ctx),
    value: resolvePlaceholderInString(row.value, ctx),
  }))
}

/** 解析参数表每行的 value（query、path、form 等） */
export function resolveParamRows(rows, ctx) {
  return (rows || []).map((row) => ({
    ...row,
    value: resolvePlaceholderInString(row.value, ctx),
  }))
}

/**
 * 收集文本里未定义或值为空的占位路径。
 * 调试台发送前用来提示「先跑登录流」，不阻断请求。
 */
export function collectUnresolvedPlaceholders(texts, ctx) {
  const found = []
  const seen = new Set()
  for (const text of texts || []) {
    if (typeof text !== 'string' || !text) continue
    for (const inner of listMustacheInners(text)) {
      const key = String(inner || '').trim()
      if (!key || seen.has(key)) continue
      const value = resolvePathSegment(ctx, key)
      if (value == null || String(value).trim() === '') {
        seen.add(key)
        found.push(key)
      }
    }
  }
  return found
}
