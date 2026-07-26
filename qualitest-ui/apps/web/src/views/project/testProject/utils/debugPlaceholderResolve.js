import { resolvePlaceholder } from '@/utils/flow/placeholder'

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
