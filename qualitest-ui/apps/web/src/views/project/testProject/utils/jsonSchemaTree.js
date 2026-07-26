/**
 * JSON Schema 子集 ↔ 树形 UI 节点（Body JSON「数据结构」）
 */

import {sanitizeSchemaNodeForPersist} from '@/views/project/testProject/utils/fieldTypeConstraints'

export function genSchemaNodeId() {
  return `sch-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 9)}`
}

export function createEmptyRootSchema() {
  return {
    type: 'object',
    properties: {}
  }
}

/**
 * @typedef {Object} SchemaUiNode
 * @property {string} id
 * @property {string} key - 字段名；根节点为空串
 * @property {string} type - object|array|string|number|integer|boolean|null|any
 * @property {string} title
 * @property {string} description
 * @property {string} mock - 标量映射到 JSON Schema default
 * @property {string} example - 标量映射到 JSON Schema example（与 mock 分列展示时使用）
 * @property {SchemaUiNode[]} children - object 的 properties 顺序列表
 * @property {SchemaUiNode|null} items - array 的元素 schema
 */

function exampleToUiString(v) {
  if (v === undefined) return ''
  if (v === null) return 'null'
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v)
    } catch {
      return String(v)
    }
  }
  return String(v)
}

/** object 子字段：新建与「加号」均为空参数名，由用户填写 */
export function createDefaultChildProperty() {
  return {
    id: genSchemaNodeId(),
    key: '',
    type: 'string',
    title: '',
    description: '',
    mock: '',
    example: '',
    /** 与 Query/Form 列一致：是否必传，落库为父 object 的 `required` 数组 */
    required: false,
    children: [],
    items: null
  }
}

function lastObjectChildKeyEmpty(children) {
  const last = children[children.length - 1]
  return !last || String(last.key || '').trim() === ''
}

/**
 * 每个 object 节点：至少保留一行空参数名；若最后一行已有名称则末尾再追加一行空行（与 KV 表一致）
 * @param {'body'|'response'} [mode] response 时不追加占位空行（响应配置与请求体编辑习惯不同）
 */
export function ensureObjectTrailingEmptyRowsDeep(node, mode = 'body') {
  if (mode === 'response') return
  if (!node || typeof node !== 'object') return
  const t = String(node.type || '').toLowerCase()
  if (t === 'object' && Array.isArray(node.children)) {
    if (node.children.length === 0 || !lastObjectChildKeyEmpty(node.children)) {
      node.children.push(createDefaultChildProperty())
    }
    for (const c of node.children) {
      ensureObjectTrailingEmptyRowsDeep(c, mode)
    }
  } else if (t === 'array' && node.items) {
    ensureObjectTrailingEmptyRowsDeep(node.items, mode)
  }
}

/**
 * @param {{ treeMode?: 'body'|'response' }} [options]
 */
export function createRootUiNode(options = {}) {
  const treeMode = options.treeMode || 'body'
  const children = treeMode === 'response' ? [] : [createDefaultChildProperty()]
  return {
    id: genSchemaNodeId(),
    key: '',
    type: 'object',
    title: '',
    description: '',
    mock: '',
    example: '',
    required: false,
    children,
    items: null
  }
}

function createLeafUiFromSchema(s, key) {
  const t = inferTypeFromSchema(s)
  const node = {
    id: genSchemaNodeId(),
    key: key ?? '',
    type: t,
    title: typeof s.title === 'string' ? s.title : '',
    description: typeof s.description === 'string' ? s.description : '',
    mock: '',
    example: '',
    required: false,
    children: [],
    items: null
  }
  if (s.default !== undefined && s.default !== null) {
    node.mock =
      typeof s.default === 'object' ? JSON.stringify(s.default) : String(s.default)
  }
  if (s.example !== undefined) {
    node.example = exampleToUiString(s.example)
  }
  attachLeafExtrasFromSchema(node, s)
  return node
}

/** 从 JSON Schema 片段读取标量扩展字段到 UI 节点 */
function attachLeafExtrasFromSchema(node, s) {
  if (!s || typeof s !== 'object') return
  if (typeof s.format === 'string' && s.format.trim()) node.format = s.format.trim()
  if (typeof s.pattern === 'string' && s.pattern.trim()) node.pattern = s.pattern.trim()
  if (typeof s.minLength === 'number' && Number.isFinite(s.minLength)) node.minLength = s.minLength
  if (typeof s.maxLength === 'number' && Number.isFinite(s.maxLength)) node.maxLength = s.maxLength
  if (typeof s.minimum === 'number' && Number.isFinite(s.minimum)) node.minimum = s.minimum
  if (typeof s.maximum === 'number' && Number.isFinite(s.maximum)) node.maximum = s.maximum
  if (typeof s.exclusiveMinimum === 'number' && Number.isFinite(s.exclusiveMinimum)) {
    node.exclusiveMinimum = s.exclusiveMinimum
  }
  if (typeof s.exclusiveMaximum === 'number' && Number.isFinite(s.exclusiveMaximum)) {
    node.exclusiveMaximum = s.exclusiveMaximum
  }
  if (typeof s.multipleOf === 'number' && Number.isFinite(s.multipleOf)) node.multipleOf = s.multipleOf
  if (typeof s.minItems === 'number' && Number.isFinite(s.minItems)) node.minItems = s.minItems
  if (typeof s.maxItems === 'number' && Number.isFinite(s.maxItems)) node.maxItems = s.maxItems
  if (s.nullable === true) node.nullable = true
  else if (Array.isArray(s.type) && s.type.includes('null')) node.nullable = true
  if (s.deprecated === true) node.deprecated = true
  if (s.readOnly === true) node.schemaBehavior = 'readOnly'
  else if (s.writeOnly === true) node.schemaBehavior = 'writeOnly'
}

function inferTypeFromSchema(s) {
  if (!s || typeof s !== 'object') return 'any'
  if (Array.isArray(s.type)) {
    const nonNull = s.type.filter((x) => x !== 'null')
    if (nonNull.length === 0) return 'null'
    return String(nonNull[0] || 'any')
  }
  if (!s.type) return 'any'
  return String(s.type)
}

/**
 * 仅剥去根 Schema 上唯一 object 属性 `data`（与统一返回 R 包装一致）；内层 properties 里的 `data` 不动。
 * @param {object|null|undefined} schema
 */
export function unwrapOuterDataObjectSchema(schema) {
  if (!schema || typeof schema !== 'object' || Array.isArray(schema)) return schema
  const props = schema.properties
  if (!props || typeof props !== 'object') return schema
  const keys = Object.keys(props)
  if (keys.length !== 1 || keys[0] !== 'data') return schema
  const dataSchema = props.data
  if (!dataSchema || typeof dataSchema !== 'object' || Array.isArray(dataSchema)) return schema
  if (inferTypeFromSchema(dataSchema) !== 'object') return schema
  const inner = JSON.parse(JSON.stringify(dataSchema))
  const ot = typeof schema.title === 'string' && schema.title.trim()
  const it = typeof inner.title === 'string' && inner.title.trim()
  if (ot && !it) inner.title = schema.title
  const od = typeof schema.description === 'string' && schema.description.trim()
  const id = typeof inner.description === 'string' && inner.description.trim()
  if (od && !id) inner.description = schema.description
  if (schema.example !== undefined && inner.example === undefined) inner.example = schema.example
  return inner
}

/**
 * 仅剥去示例 JSON 最外层单键 `data`（值为普通 object 时）；数组或其它形态保持不动。
 * @param {*} value
 */
export function unwrapOuterDataExampleJson(value) {
  if (value == null || typeof value !== 'object' || Array.isArray(value)) return value
  const keys = Object.keys(value)
  if (keys.length !== 1 || keys[0] !== 'data') return value
  const inner = value.data
  if (inner == null || typeof inner !== 'object' || Array.isArray(inner)) return value
  return inner
}

function copyLeafIntoRoot(root, leaf) {
  root.type = leaf.type
  root.title = leaf.title
  root.description = leaf.description
  root.mock = leaf.mock
  root.example = leaf.example || ''
  root.required = leaf.required === true
  root.children = []
  root.items = leaf.items || null
  copyLeafExtrasBetween(leaf, root)
}

function copyLeafExtrasBetween(from, to) {
  const KEYS = [
    'format',
    'pattern',
    'minLength',
    'maxLength',
    'minimum',
    'maximum',
    'exclusiveMinimum',
    'exclusiveMaximum',
    'multipleOf',
    'minItems',
    'maxItems',
    'nullable',
    'deprecated',
    'schemaBehavior'
  ]
  for (const k of KEYS) {
    if (from[k] !== undefined) to[k] = from[k]
    else delete to[k]
  }
}

/** Body 单列「参数值」：仅 default 缺省时用 example 填满 mock，避免旧数据只含 example 时不显示 */
function mergeExampleIntoMockWhenDefaultMissing(node) {
  if (!node) return
  const t = String(node.type || '').toLowerCase()
  if (t === 'object' && Array.isArray(node.children)) {
    for (const c of node.children) mergeExampleIntoMockWhenDefaultMissing(c)
  } else if (t === 'array' && node.items) {
    mergeExampleIntoMockWhenDefaultMissing(node.items)
  } else {
    const m = String(node.mock ?? '').trim()
    const ex = String(node.example ?? '').trim()
    if (!m && ex) {
      node.mock = node.example
      node.example = ''
    }
  }
}

/**
 * JSON Schema → UI 根节点（根可与 API 文档一致为 object / array / 标量等）
 * @param {object} [options]
 * @param {boolean} [options.mergeExampleIntoMock=true] Body 单列时把仅有 example 的标量合并进 mock
 */
export function schemaJsonToUiRoot(schema, options = {}) {
  const mergeEx = options.mergeExampleIntoMock !== false
  const treeMode = options.treeMode || 'body'
  const root = createRootUiNode({treeMode})
  if (!schema || typeof schema !== 'object') {
    ensureObjectTrailingEmptyRowsDeep(root, treeMode)
    return root
  }

  const t = inferTypeFromSchema(schema)
  const props = schema.properties
  const isObjectShape =
      t === 'object' || (props && typeof props === 'object')

  if (isObjectShape) {
    const src =
        treeMode === 'response' ? unwrapOuterDataObjectSchema(schema) : schema
    root.type = 'object'
    root.title = typeof src.title === 'string' ? src.title : ''
    root.description = typeof src.description === 'string' ? src.description : ''
    root.mock = ''
    root.example = src.example !== undefined ? exampleToUiString(src.example) : ''
    root.children = []
    const p = (src.properties && typeof src.properties === 'object' ? src.properties : {}) || {}
    const rootReq = new Set(Array.isArray(src.required) ? src.required.map(String) : [])
    for (const k of Object.keys(p)) {
      const ch = schemaToUiNode(p[k], k)
      ch.required = rootReq.has(k)
      root.children.push(ch)
    }
    if (mergeEx) mergeExampleIntoMockWhenDefaultMissing(root)
    ensureObjectTrailingEmptyRowsDeep(root, treeMode)
    return root
  }

  if (t === 'array' || schema.items) {
    root.type = 'array'
    root.title = typeof schema.title === 'string' ? schema.title : ''
    root.description = typeof schema.description === 'string' ? schema.description : ''
    root.mock = ''
    root.example = schema.example !== undefined ? exampleToUiString(schema.example) : ''
    root.children = []
    root.items = schema.items ? schemaToUiNode(schema.items, '') : createLeafUiFromSchema({type: 'string'}, '')
    attachLeafExtrasFromSchema(root, schema)
    if (mergeEx) mergeExampleIntoMockWhenDefaultMissing(root)
    ensureObjectTrailingEmptyRowsDeep(root, treeMode)
    return root
  }

  const leaf = schemaToUiNode(schema, '')
  copyLeafIntoRoot(root, leaf)
  if (mergeEx) mergeExampleIntoMockWhenDefaultMissing(root)
  ensureObjectTrailingEmptyRowsDeep(root, treeMode)
  return root
}

function schemaToUiNode(s, key) {
  if (!s || typeof s !== 'object') {
    const leaf = createLeafUiFromSchema({type: 'string'}, key)
    leaf.key = key
    return leaf
  }

  const rawType = inferTypeFromSchema(s)

  if (rawType === 'object' || (s.properties && typeof s.properties === 'object')) {
    const node = {
      id: genSchemaNodeId(),
      key,
      type: 'object',
      title: typeof s.title === 'string' ? s.title : '',
      description: typeof s.description === 'string' ? s.description : '',
      mock: '',
      example: s.example !== undefined ? exampleToUiString(s.example) : '',
      required: false,
      children: [],
      items: null
    }
    const props = s.properties || {}
    const reqSet = new Set(Array.isArray(s.required) ? s.required.map(String) : [])
    for (const k of Object.keys(props)) {
      const child = schemaToUiNode(props[k], k)
      child.required = reqSet.has(k)
      node.children.push(child)
    }
    return node
  }

  if (rawType === 'array' || s.items) {
    const node = {
      id: genSchemaNodeId(),
      key,
      type: 'array',
      title: typeof s.title === 'string' ? s.title : '',
      description: typeof s.description === 'string' ? s.description : '',
      mock: '',
      example: s.example !== undefined ? exampleToUiString(s.example) : '',
      required: false,
      children: [],
      items: null
    }
    node.items = s.items ? schemaToUiNode(s.items, '') : createLeafUiFromSchema({type: 'string'}, '')
    attachLeafExtrasFromSchema(node, s)
    return node
  }

  if (rawType === 'null') {
    const n = createLeafUiFromSchema({...s, type: 'null'}, key)
    n.type = 'null'
    return n
  }

  const scalarTypes = ['string', 'number', 'integer', 'boolean']
  if (scalarTypes.includes(rawType)) {
    const n = createLeafUiFromSchema(s, key)
    n.type = rawType
    return n
  }

  const leaf = createLeafUiFromSchema(s, key)
  leaf.type = rawType === 'any' ? 'any' : leaf.type
  return leaf
}

function attachExampleKeywordFromNode(out, node, includeExample) {
  if (!includeExample) return
  const ex = String(node.example ?? '').trim()
  if (!ex) return
  try {
    out.example = JSON.parse(ex)
  } catch {
    out.example = ex
  }
}

/**
 * UI 根节点 → JSON Schema 对象
 * @param {object} [options]
 * @param {boolean} [options.includeExample=false] 是否写入 JSON Schema example（响应配置双列时为 true）
 */
export function uiRootToSchemaJson(root, options = {}) {
  if (!root || typeof root !== 'object') return createEmptyRootSchema()
  return uiNodeToSchema(root, options)
}

/** 用于对比「是否与空 object 根等价」（避免 null schema 与默认树互相触发写入） */
export function isEmptyObjectSchema(schema) {
  if (!schema || typeof schema !== 'object') return true
  if (typeof schema.title === 'string' && schema.title.trim()) return false
  if (typeof schema.description === 'string' && schema.description.trim()) return false
  const t = String(schema.type || '').toLowerCase()
  if (t !== 'object') return false
  const p = schema.properties
  if (!p || typeof p !== 'object') return true
  return Object.keys(p).length === 0
}

function resolveNormalizedSchemaType(raw, out) {
  const src = out.type !== undefined ? out.type : raw.type
  if (Array.isArray(src)) {
    const nn = src.filter((x) => x !== 'null')
    return String(nn[0] || 'string').toLowerCase()
  }
  return String(src || '').toLowerCase()
}

function copySchemaNumIfFinite(raw, out, key) {
  if (!(key in raw)) return
  const v = raw[key]
  if (typeof v === 'number' && Number.isFinite(v)) out[key] = v
}

/**
 * 落库 walk：在 object/array 子树处理完后，把标量叶上的校验关键字从 raw 抄回 out（与 uiNodeToSchema 写入的字段一致）
 */
function mergeLeafValidationFromRaw(raw, out) {
  if (raw.properties && typeof raw.properties === 'object') return
  const nty = resolveNormalizedSchemaType(raw, out)
  if (nty === 'array') {
    copySchemaNumIfFinite(raw, out, 'minItems')
    copySchemaNumIfFinite(raw, out, 'maxItems')
    if (raw.nullable === true) out.nullable = true
    if (raw.deprecated === true) out.deprecated = true
    return
  }

  if (nty === 'string' || nty === 'any') {
    copySchemaNumIfFinite(raw, out, 'minLength')
    copySchemaNumIfFinite(raw, out, 'maxLength')
    if (typeof raw.pattern === 'string' && raw.pattern.trim()) out.pattern = raw.pattern.trim()
    if (typeof raw.format === 'string' && raw.format.trim()) out.format = raw.format.trim()
  }
  if (nty === 'integer' || nty === 'number') {
    copySchemaNumIfFinite(raw, out, 'minimum')
    copySchemaNumIfFinite(raw, out, 'maximum')
    copySchemaNumIfFinite(raw, out, 'exclusiveMinimum')
    copySchemaNumIfFinite(raw, out, 'exclusiveMaximum')
    copySchemaNumIfFinite(raw, out, 'multipleOf')
  }
  if (raw.nullable === true) out.nullable = true
  if (raw.deprecated === true) out.deprecated = true
  if (raw.readOnly === true) out.readOnly = true
  if (raw.writeOnly === true) out.writeOnly = true
}

/**
 * 保存 requestConfig 前清理 body.json.schema：
 * 去掉首尾空白、空字符串字段、空 default、properties 中的空键名；递归子节点。
 * 若与「仅 type:object + 空 properties」等价则返回 null，避免落库无效对象。
 * @param {object} [options]
 * @param {boolean} [options.unwrapOuterData] 为 true 时仅对根节点剥去唯一 `data` object 包装（响应用；请求体勿开）
 */
export function sanitizeBodyJsonSchemaForPersist(schema, options = {}) {
  if (schema == null) return null
  const rootIn =
      options.unwrapOuterData === true ? unwrapOuterDataObjectSchema(schema) : schema

  function walk(node) {
    if (node === null || typeof node !== 'object' || Array.isArray(node)) {
      return node
    }

    const raw = JSON.parse(JSON.stringify(node))
    const out = {}

    if ('type' in raw && raw.type !== '' && raw.type !== undefined) {
      out.type = raw.type
    }

    if (typeof raw.title === 'string' && raw.title.trim()) {
      out.title = raw.title.trim()
    }
    if (typeof raw.description === 'string' && raw.description.trim()) {
      out.description = raw.description.trim()
    }

    if ('default' in raw) {
      const d = raw.default
      if (d !== undefined && d !== null && d !== '') {
        if (typeof d === 'string') {
          const dt = d.trim()
          if (dt !== '') out.default = dt
        } else {
          out.default = d
        }
      }
    }

    if ('example' in raw) {
      const d = raw.example
      if (d === undefined || d === null) {
        /* skip */
      } else if (typeof d === 'string' && !d.trim()) {
        /* skip */
      } else if (
          typeof d === 'object' &&
          !Array.isArray(d) &&
          d !== null &&
          Object.keys(d).length === 0
      ) {
        /* skip empty object example */
      } else {
        out.example = d
      }
    }

    if (raw.properties && typeof raw.properties === 'object') {
      const props = {}
      for (const [k, v] of Object.entries(raw.properties)) {
        const name = String(k).trim()
        if (!name) continue
        const child = walk(v)
        if (isEmptyObjectSchema(child)) continue
        props[name] = child
      }
      out.properties = props
      if (Array.isArray(raw.required) && raw.required.length) {
        const names = new Set(Object.keys(props))
        const filtered = raw.required.map(String).filter((k) => names.has(k))
        if (filtered.length) out.required = filtered
      }
    }

    if (raw.items != null && typeof raw.items === 'object') {
      const it = walk(raw.items)
      out.items = it != null ? it : {type: 'string'}
    }

    const ty = String(out.type || raw.type || '').toLowerCase()
    if (ty === 'array' && !out.items) {
      out.items = {type: 'string'}
    }

    mergeLeafValidationFromRaw(raw, out)

    sanitizeSchemaNodeForPersist(out)

    return out
  }

  const result = walk(rootIn)
  if (isEmptyObjectSchema(result)) return null
  return result
}

/** 与 {@link sanitizeBodyJsonSchemaForPersist} 相同，用于响应 Schema 等场景 */
export const sanitizeJsonSchemaForPersist = sanitizeBodyJsonSchemaForPersist

/** UI 标量节点 → JSON Schema 片段：写入 format / pattern / nullable 等 */
function attachLeafExtrasToSchemaOut(out, node, t) {
  const ty = String(t || '').toLowerCase()
  if (typeof node.format === 'string' && node.format.trim()) out.format = node.format.trim()
  if (typeof node.pattern === 'string' && node.pattern.trim()) out.pattern = node.pattern.trim()
  if (ty === 'string' || ty === 'any') {
    if (typeof node.minLength === 'number' && Number.isFinite(node.minLength)) out.minLength = node.minLength
    if (typeof node.maxLength === 'number' && Number.isFinite(node.maxLength)) out.maxLength = node.maxLength
  }
  if (ty === 'integer' || ty === 'number') {
    if (typeof node.minimum === 'number' && Number.isFinite(node.minimum)) out.minimum = node.minimum
    if (typeof node.maximum === 'number' && Number.isFinite(node.maximum)) out.maximum = node.maximum
    if (typeof node.exclusiveMinimum === 'number' && Number.isFinite(node.exclusiveMinimum)) {
      out.exclusiveMinimum = node.exclusiveMinimum
    }
    if (typeof node.exclusiveMaximum === 'number' && Number.isFinite(node.exclusiveMaximum)) {
      out.exclusiveMaximum = node.exclusiveMaximum
    }
    if (typeof node.multipleOf === 'number' && Number.isFinite(node.multipleOf)) out.multipleOf = node.multipleOf
  }
  if (node.nullable === true) out.nullable = true
  if (node.deprecated === true) out.deprecated = true
  if (node.schemaBehavior === 'readOnly') out.readOnly = true
  else if (node.schemaBehavior === 'writeOnly') out.writeOnly = true
}

function attachArrayConstraintsToSchemaOut(out, node) {
  if (typeof node.minItems === 'number' && Number.isFinite(node.minItems)) out.minItems = node.minItems
  if (typeof node.maxItems === 'number' && Number.isFinite(node.maxItems)) out.maxItems = node.maxItems
}

function uiNodeToSchema(node, options = {}) {
  const includeExample = options.includeExample === true
  const t = String(node.type || 'string').toLowerCase()

  if (t === 'object') {
    const properties = {}
    const requiredList = []
    for (const c of node.children || []) {
      const k = String(c.key || '').trim()
      if (!k) continue
      properties[k] = uiNodeToSchema(c, options)
      if (c.required === true) requiredList.push(k)
    }
    const out = {type: 'object', properties}
    if (requiredList.length) out.required = requiredList
    if (node.title) out.title = node.title
    if (node.description) out.description = node.description
    attachExampleKeywordFromNode(out, node, includeExample)
    return out
  }

  if (t === 'array') {
    const items = node.items ? uiNodeToSchema(node.items, options) : {type: 'string'}
    const out = {type: 'array', items}
    if (node.title) out.title = node.title
    if (node.description) out.description = node.description
    attachExampleKeywordFromNode(out, node, includeExample)
    attachArrayConstraintsToSchemaOut(out, node)
    return out
  }

  if (t === 'any') {
    const out = {}
    if (node.title) out.title = node.title
    if (node.description) out.description = node.description
    attachExampleKeywordFromNode(out, node, includeExample)
    attachLeafExtrasToSchemaOut(out, node, t)
    return Object.keys(out).length ? out : {}
  }

  if (t === 'null') {
    const out = {type: 'null'}
    if (node.title) out.title = node.title
    if (node.description) out.description = node.description
    attachExampleKeywordFromNode(out, node, includeExample)
    attachLeafExtrasToSchemaOut(out, node, t)
    return out
  }

  const out = {type: t}
  if (node.title) out.title = node.title
  if (node.description) out.description = node.description
  const mock = String(node.mock ?? '').trim()
  if (mock) {
    if (t === 'integer' || t === 'number') {
      const n = Number(mock)
      if (Number.isFinite(n)) out.default = t === 'integer' ? parseInt(mock, 10) : n
      else out.default = mock
    } else if (t === 'boolean') {
      out.default = mock === 'true' || mock === '1'
    } else {
      out.default = mock
    }
  }
  attachExampleKeywordFromNode(out, node, includeExample)
  attachLeafExtrasToSchemaOut(out, node, t)
  return out
}

/**
 * 扁平化展示行（深度优先；array 的 items 子树紧随其后）
 * @param {object} [options]
 * @param {boolean} [options.hideRootObjectShell] 为 true 时不展示最外层 object「壳」，第一层字段从深度 0 开始（用于响应配置，避免「根节点」多一层）
 * @param {boolean} [options.unwrapOuterDataWrapper] 与 hideRootObjectShell 配合：仅当根下唯一子节点为 object 且键名为 data 时，再跳过这一行（统一返回最外层 data），内层同名 data 字段不受影响
 */
export function flattenSchemaUiRows(root, options = {}) {
  const hideRootObjectShell = options.hideRootObjectShell === true
  const unwrapOuterDataWrapper = options.unwrapOuterDataWrapper === true
  const acc = []

  function shouldUnwrapSingleDataProperty(children) {
    if (!unwrapOuterDataWrapper || !Array.isArray(children) || children.length !== 1) {
      return false
    }
    const only = children[0]
    if (String(only.key || '').trim() !== 'data') return false
    if (String(only.type || '').toLowerCase() !== 'object') return false
    return true
  }

  /** 仅跳过「整棵树真正的根」那一行 object 壳；内层 object（如业务字段 data）必须照常展示 */
  function walk(node, depth, isTreeRoot) {
    if (!node) return
    const hideThisRow =
        hideRootObjectShell &&
        isTreeRoot &&
        depth === 0 &&
        String(node.type || '').toLowerCase() === 'object'
    if (hideThisRow) {
      const children = node.children || []
      if (shouldUnwrapSingleDataProperty(children)) {
        const dataNode = children[0]
        for (const c of dataNode.children || []) {
          walk(c, 0, false)
        }
      } else {
        for (const c of children) {
          walk(c, 0, false)
        }
      }
      return
    }
    acc.push({node, depth})
    if (node.type === 'object' && Array.isArray(node.children)) {
      for (const c of node.children) walk(c, depth + 1, false)
    }
    if (node.type === 'array' && node.items) {
      walk(node.items, depth + 1, false)
    }
  }
  walk(root, 0, true)
  return acc
}

export const SCHEMA_JSON_BODY_TYPES = [
  'object',
  'array',
  'string',
  'number',
  'integer',
  'boolean',
  'null',
  'any'
]
