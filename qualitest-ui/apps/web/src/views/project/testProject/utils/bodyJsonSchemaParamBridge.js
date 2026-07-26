import {createDefaultChildProperty, SCHEMA_JSON_BODY_TYPES} from '@/views/project/testProject/utils/jsonSchemaTree'
import {pruneConstraintsForType} from '@/views/project/testProject/utils/fieldTypeConstraints'

/** 与 ApiDebugTab.emptyKVRow 字段一致，供参数高级弹窗复用 */
export function emptyParamSchemaRowShape() {
  return {
    _enabled: true,
    name: '',
    value: '',
    type: 'string',
    description: '',
    required: false,
    nullable: true,
    deprecated: false,
    format: '',
    behavior: 'readWrite',
    minLength: null,
    maxLength: null,
    minValue: null,
    maxValue: null,
    exclusiveMinimum: null,
    exclusiveMaximum: null,
    multipleOf: null,
    minItems: null,
    maxItems: null,
    defaultValue: '',
    pattern: ''
  }
}

export function ensureParamSchemaRowDefaults(row) {
  if (!row || typeof row !== 'object') return
  const d = emptyParamSchemaRowShape()
  for (const key of Object.keys(d)) {
    if (row[key] === undefined) row[key] = d[key]
  }
}

function parseOptionalNumber(v) {
  if (v === '' || v == null) return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

function writeOptionalNumber(target, key, value) {
  const n = parseOptionalNumber(value)
  if (n != null) target[key] = n
  else delete target[key]
}

/**
 * Schema UI 标量/array 节点 → 与参数高级弹窗同一形状的 plain 对象
 */
export function bodyJsonSchemaNodeToParamRow(node, emptyFactory = emptyParamSchemaRowShape) {
  const row = emptyFactory()
  row.name = String(node.key ?? '')
  row.value = String(node.mock ?? '')
  row.type = String(node.type ?? 'string')
  row.description = String(node.description ?? '')
  row.required = node.required === true
  row.nullable = node.nullable === true
  row.deprecated = node.deprecated === true
  row.format = typeof node.format === 'string' ? node.format : ''
  const sb = node.schemaBehavior
  row.behavior =
      sb === 'readOnly' || node.readOnly === true
        ? 'readOnly'
        : sb === 'writeOnly' || node.writeOnly === true
          ? 'writeOnly'
          : 'readWrite'
  row.minLength = parseOptionalNumber(node.minLength)
  row.maxLength = parseOptionalNumber(node.maxLength)
  row.minValue = parseOptionalNumber(node.minimum)
  row.maxValue = parseOptionalNumber(node.maximum)
  row.exclusiveMinimum = parseOptionalNumber(node.exclusiveMinimum)
  row.exclusiveMaximum = parseOptionalNumber(node.exclusiveMaximum)
  row.multipleOf = parseOptionalNumber(node.multipleOf)
  row.minItems = parseOptionalNumber(node.minItems)
  row.maxItems = parseOptionalNumber(node.maxItems)
  row.defaultValue = String(node.mock ?? '')
  row.pattern = typeof node.pattern === 'string' ? node.pattern : ''
  return row
}

/**
 * 将弹窗行写回 Schema UI 节点（就地修改）
 */
export function paramRowMergeIntoBodyJsonNode(node, row, ensureParamDefaults = ensureParamSchemaRowDefaults) {
  if (!node || !row) return
  const oldType = String(node.type || '').toLowerCase()
  ensureParamDefaults(row)
  let newType = String(row.type || 'string').toLowerCase()
  if (!SCHEMA_JSON_BODY_TYPES.includes(newType)) newType = 'string'

  node.type = newType
  node.description = row.description != null ? String(row.description) : ''
  node.required = row.required === true
  node.mock = row.defaultValue != null ? String(row.defaultValue) : ''

  if (row.nullable === true) node.nullable = true
  else delete node.nullable

  if (row.deprecated === true) node.deprecated = true
  else delete node.deprecated

  const fmt = row.format != null ? String(row.format).trim() : ''
  if (fmt) node.format = fmt
  else delete node.format

  const pat = row.pattern != null ? String(row.pattern).trim() : ''
  if (pat) node.pattern = pat
  else delete node.pattern

  writeOptionalNumber(node, 'minLength', row.minLength)
  writeOptionalNumber(node, 'maxLength', row.maxLength)
  writeOptionalNumber(node, 'minimum', row.minValue)
  writeOptionalNumber(node, 'maximum', row.maxValue)
  writeOptionalNumber(node, 'exclusiveMinimum', row.exclusiveMinimum)
  writeOptionalNumber(node, 'exclusiveMaximum', row.exclusiveMaximum)
  writeOptionalNumber(node, 'multipleOf', row.multipleOf)
  writeOptionalNumber(node, 'minItems', row.minItems)
  writeOptionalNumber(node, 'maxItems', row.maxItems)

  delete node.readOnly
  delete node.writeOnly
  delete node.schemaBehavior
  const b = row.behavior === 'readOnly' || row.behavior === 'writeOnly' ? row.behavior : 'readWrite'
  if (b === 'readOnly') node.schemaBehavior = 'readOnly'
  else if (b === 'writeOnly') node.schemaBehavior = 'writeOnly'

  if (oldType !== newType) {
    if (newType === 'object') {
      node.items = null
      node.children = []
    } else if (newType === 'array') {
      node.children = []
      if (!node.items) {
        const leaf = createDefaultChildProperty()
        leaf.type = 'string'
        node.items = leaf
      }
    } else {
      node.children = []
      node.items = null
    }
  }

  delete node.enum
  delete node.const
  pruneConstraintsForType(node, newType, { flatParam: false })
}

/**
 * 将弹窗行写回扁平 KV 参数行（query/path/form-data）
 */
export function paramRowMergeIntoFlatParamRow(row, dialogRow) {
  if (!row || !dialogRow) return
  ensureParamSchemaRowDefaults(dialogRow)
  let newType = String(dialogRow.type || 'string').toLowerCase()
  row.type = newType
  row.description = dialogRow.description != null ? String(dialogRow.description) : ''
  row.required = dialogRow.required === true
  row.nullable = dialogRow.nullable === true
  row.deprecated = dialogRow.deprecated === true
  row.format = dialogRow.format != null ? String(dialogRow.format) : ''
  row.behavior =
      dialogRow.behavior === 'readOnly' || dialogRow.behavior === 'writeOnly'
        ? dialogRow.behavior
        : 'readWrite'
  row.minLength = parseOptionalNumber(dialogRow.minLength)
  row.maxLength = parseOptionalNumber(dialogRow.maxLength)
  row.minValue = parseOptionalNumber(dialogRow.minValue)
  row.maxValue = parseOptionalNumber(dialogRow.maxValue)
  row.minItems = parseOptionalNumber(dialogRow.minItems)
  row.maxItems = parseOptionalNumber(dialogRow.maxItems)
  row.defaultValue = dialogRow.defaultValue != null ? String(dialogRow.defaultValue) : ''
  row.pattern = dialogRow.pattern != null ? String(dialogRow.pattern) : ''
  if (dialogRow.defaultValue != null && String(dialogRow.defaultValue).trim()) {
    row.value = String(dialogRow.defaultValue)
  }
  pruneConstraintsForType(row, newType, { flatParam: true })
}
