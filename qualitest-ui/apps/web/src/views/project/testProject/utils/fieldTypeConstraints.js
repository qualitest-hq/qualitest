/**
 * 字段类型与结构约束键的对应关系及裁剪规则。
 */

function normType(type) {
  return String(type || '').toLowerCase()
}

export function supportsStringConstraints(type) {
  const t = normType(type)
  return t === 'string' || t === 'file' || t === 'any'
}

export function supportsNumberConstraints(type) {
  const t = normType(type)
  return t === 'integer' || t === 'number'
}

export function supportsArrayConstraints(type) {
  return normType(type) === 'array'
}

export function supportsFormat(type) {
  // format 仅适用于 string/file/any
  return supportsStringConstraints(type)
}

const STRING_KEYS = ['pattern', 'minLength', 'maxLength', 'format']
const NUMBER_SCHEMA_KEYS = ['minimum', 'maximum', 'exclusiveMinimum', 'exclusiveMaximum', 'multipleOf']
const NUMBER_FLAT_KEYS = ['minValue', 'maxValue', 'exclusiveMinimum', 'exclusiveMaximum', 'multipleOf']
const ARRAY_KEYS = ['minItems', 'maxItems']
const OBJECT_KEYS = ['properties', 'required']
const DEPRECATED_KEYS = ['enum', 'const', 'enumEnabled', 'constantEnabled']

function deleteKeys(target, keys) {
  if (!target || typeof target !== 'object') return
  for (const k of keys) {
    delete target[k]
  }
}

/**
 * 改 type 后按矩阵清理不兼容约束（就地修改 target）
 * @param {object} target KV 行或 schema UI 节点
 * @param {string} type 新类型
 * @param {{ flatParam?: boolean }} [options] flatParam=true 时用 minValue/maxValue
 */
export function pruneConstraintsForType(target, type, options = {}) {
  if (!target || typeof target !== 'object') return
  const t = normType(type)
  const flat = options.flatParam === true

  if (!supportsStringConstraints(t)) {
    deleteKeys(target, STRING_KEYS)
  }
  if (!supportsNumberConstraints(t)) {
    deleteKeys(target, flat ? NUMBER_FLAT_KEYS : NUMBER_SCHEMA_KEYS)
    if (!flat) {
      deleteKeys(target, ['minValue', 'maxValue'])
    } else {
      deleteKeys(target, ['minimum', 'maximum'])
    }
  }
  if (!supportsArrayConstraints(t)) {
    deleteKeys(target, ARRAY_KEYS)
  }
  if (t !== 'object') {
    deleteKeys(target, OBJECT_KEYS)
  }
  deleteKeys(target, DEPRECATED_KEYS)
}

/**
 * 保存前清洗 KV 参数行：按当前 type prune，并剥离 enum/const 相关键
 * @param {object} row
 * @returns {object}
 */
export function sanitizeParamRowForPersist(row) {
  if (!row || typeof row !== 'object') return row
  const out = {...row}
  pruneConstraintsForType(out, out.type, { flatParam: true })
  return out
}

/**
 * 保存前清洗 schema 节点（walk 内对每个节点调用）：剥离 enum/const 并按 type prune
 * @param {object} node 已组装的 schema 片段（out）
 */
export function sanitizeSchemaNodeForPersist(node) {
  if (!node || typeof node !== 'object' || Array.isArray(node)) return
  const t = resolveSchemaNodeType(node)
  delete node.enum
  delete node.const
  pruneConstraintsForType(node, t, { flatParam: false })
}

function resolveSchemaNodeType(node) {
  if (node.properties && typeof node.properties === 'object') return 'object'
  if (node.items != null) return 'array'
  return normType(node.type) || 'string'
}
