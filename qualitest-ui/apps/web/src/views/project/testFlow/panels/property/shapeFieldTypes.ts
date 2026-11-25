/**
 * HTTP 入口 inputShape 用的字段类型表与工具。
 * body 可挂 object/json 以支持 children 嵌套；query、headers 也用同一套下拉值。
 */

/** 形状字段可选类型（下拉展示） */
export const SHAPE_FIELD_TYPES = [
  { value: 'text', label: '文本' },
  { value: 'number', label: '数字' },
  { value: 'boolean', label: '布尔' },
  { value: 'object', label: '对象' },
  { value: 'json', label: 'JSON' },
] as const

export type ShapeFieldType = (typeof SHAPE_FIELD_TYPES)[number]['value']

const KNOWN = new Set<string>(SHAPE_FIELD_TYPES.map((t) => t.value))

/**
 * 规范化形状字段 type：空或未知回退 text；别名 string 视为 text。
 */
export function normalizeShapeType(type: unknown): ShapeFieldType {
  const s = String(type || 'text').trim().toLowerCase()
  if (s === 'string') return 'text'
  return (KNOWN.has(s) ? s : 'text') as ShapeFieldType
}

/**
 * 该类型是否允许挂 children 子字段（仅 object、json）。
 */
export function canHaveChildren(type: unknown): boolean {
  const t = normalizeShapeType(type)
  return t === 'object' || t === 'json'
}
