/** 变量条目 KV 表可选类型（素材库、环境变量共用） */
export const VARIABLE_ENTRY_PARAM_TYPES = [
  'string',
  'integer',
  'number',
  'boolean',
  'array',
  'object',
  'file'
]

const COMPOSITE_TYPES = new Set(['object', 'array'])
const ENTRY_ROW_KIND = 'entry'

export function paramTypeSelectClass(type) {
  const raw = type == null || type === '' ? 'string' : String(type)
  const safe = raw.toLowerCase().replace(/[^a-z0-9]/g, '') || 'custom'
  return ['param-type-select', `is-type-${safe}`]
}

export function extractEntryInner(data) {
  if (!data) return undefined
  const key = data.key
  const assets = data.assets
  if (assets && key != null && assets[key] !== undefined) return assets[key]
  if (assets && typeof assets === 'object') {
    const keys = Object.keys(assets)
    if (keys.length === 1) return assets[keys[0]]
  }
  return undefined
}

export function wrapAssetsPayload(key, inner) {
  const k = (key || '').trim()
  return { [k]: inner }
}

/**
 * 解析变量条目 JSON 数组（读库/预览用，不校验 id）
 * @param {string|null|undefined} raw
 * @returns {{ id: number|null, key: string, remark: string, assets: object }[]}
 */
export function parseVariableEntries(raw) {
  const s = (raw || '').trim()
  if (!s) return []
  try {
    const j = JSON.parse(s)
    if (!Array.isArray(j)) return []
    return j.map((item) => ({
      id: item?.id != null ? item.id : null,
      key: item?.key != null ? String(item.key) : '',
      remark: item?.remark != null ? String(item.remark) : '',
      assets: item?.assets && typeof item.assets === 'object' ? item.assets : {}
    }))
  } catch {
    return []
  }
}

export function inferValueType(value) {
  if (value === null) return 'null'
  if (Array.isArray(value)) return 'array'
  if (typeof value === 'object') {
    if (value.type === 'file') return 'file'
    return 'object'
  }
  if (typeof value === 'boolean') return 'boolean'
  if (typeof value === 'number') {
    return Number.isInteger(value) ? 'integer' : 'number'
  }
  return 'string'
}

function scalarToDisplay(value, type) {
  const t = String(type || 'string').toLowerCase()
  if (t === 'null') return ''
  if (t === 'boolean') return String(value)
  if (t === 'integer' || t === 'number') return String(value)
  // file 对象在表格里优先展示存储路径，便于复制与引用
  if (t === 'file' && value && typeof value === 'object') {
    return value.storagePath || value.fileName || JSON.stringify(value)
  }
  if (value == null) return ''
  return String(value)
}

/** 从路径中取最后一段文件名（用于 file 条目缺省 fileName） */
function fileBaseName(path) {
  const p = String(path || '').replace(/\\/g, '/')
  const i = p.lastIndexOf('/')
  return i >= 0 ? p.slice(i + 1) : p
}

/**
 * 构造一张扁平行。
 * uploadedOriginalName：file 行的用户原始文件名，展示与回写用，不入库到行 value。
 */
function makeSheetRow({
  key = '',
  remark = '',
  value = '',
  type = 'string',
  depth = 0,
  rowKind = 'field',
  entryId = null,
  entryKey = '',
  hideKey = false,
  uploadedOriginalName = ''
}) {
  const row = {
    key: hideKey ? '' : key,
    remark: rowKind === ENTRY_ROW_KIND ? (remark ?? '') : '',
    value,
    type,
    _enabled: true,
    _depth: depth,
    _rowKind: rowKind,
    _entryId: entryId,
    _entryKey: entryKey,
    _hideKey: hideKey
  }
  if (uploadedOriginalName) {
    row._uploadedOriginalName = uploadedOriginalName
  }
  return row
}

/**
 * 从已落库的 file 对象取出原始文件名，展开到表格行时挂到 _uploadedOriginalName，
 * 避免只显示 storagePath 时丢失用户文件名。
 */
function fileMetaFromValue(value, type) {
  if (String(type || '').toLowerCase() !== 'file' || !value || typeof value !== 'object') {
    return {}
  }
  const name = value.fileName != null ? String(value.fileName).trim() : ''
  return name ? { uploadedOriginalName: name } : {}
}

/** 按顶级 entry 行切分 [start, end) 块索引 */
export function collectEntryBlocks(rows) {
  const blocks = []
  let i = 0
  while (i < rows.length) {
    if ((rows[i]._depth ?? 0) !== 0 || rows[i]._rowKind !== ENTRY_ROW_KIND) {
      i++
      continue
    }
    let j = i + 1
    while (j < rows.length && (rows[j]._depth ?? 0) > 0) {
      j++
    }
    blocks.push({ start: i, end: j })
    i = j
  }
  return blocks
}

export function indexOfSheetRow(rows, row) {
  if (!row) return -1
  const idx = rows.indexOf(row)
  return idx >= 0 ? idx : -1
}

export function findParentSheetRow(rows, index) {
  const row = rows[index]
  if (!row) return null
  const depth = row._depth ?? 0
  if (depth <= 0) return null
  for (let i = index - 1; i >= 0; i--) {
    if ((rows[i]._depth ?? 0) === depth - 1) return rows[i]
  }
  return null
}

export function syncSheetRowKeyFlags(rows) {
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]
    if ((row._depth ?? 0) === 0) {
      row._hideKey = false
      continue
    }
    const parent = findParentSheetRow(rows, i)
    const parentType = String(parent?.type || '').toLowerCase()
    const hideKey = parentType === 'array'
    row._hideKey = hideKey
    if (hideKey) row.key = ''
  }
}

function isCompositeType(type) {
  return COMPOSITE_TYPES.has(String(type || '').toLowerCase())
}

function expandValueToFieldRows(inner, depth, entryId, entryKey) {
  const rows = []
  if (inner == null) return rows

  if (Array.isArray(inner)) {
    inner.forEach((item) => {
      const type = inferValueType(item)
      const row = makeSheetRow({
        key: '',
        value: isCompositeType(type) ? '' : scalarToDisplay(item, type),
        type,
        depth,
        rowKind: 'field',
        entryId,
        entryKey,
        hideKey: true,
        ...fileMetaFromValue(item, type)
      })
      rows.push(row)
      if (isCompositeType(type)) {
        rows.push(...expandValueToFieldRows(item, depth + 1, entryId, entryKey))
      }
    })
    return rows
  }

  if (typeof inner === 'object') {
    for (const [fieldKey, val] of Object.entries(inner)) {
      const type = inferValueType(val)
      const row = makeSheetRow({
        key: fieldKey,
        value: isCompositeType(type) ? '' : scalarToDisplay(val, type),
        type,
        depth,
        rowKind: 'field',
        entryId,
        entryKey,
        ...fileMetaFromValue(val, type)
      })
      rows.push(row)
      if (isCompositeType(type)) {
        rows.push(...expandValueToFieldRows(val, depth + 1, entryId, entryKey))
      }
    }
  }
  return rows
}

/**
 * API 变量条目列表 → 单表扁平行（顶级为 entry，object/array 下挂子行）
 */
export function entriesToSheetRows(entries) {
  const rows = []
  for (const entry of entries || []) {
    const key = (entry?.key || '').trim()
    const inner = extractEntryInner(entry)
    const type = inferValueType(inner)
    const remark = (entry?.remark ?? '').trim()
    rows.push(
      makeSheetRow({
        key,
        remark,
        value: isCompositeType(type) ? '' : scalarToDisplay(inner, type),
        type,
        depth: 0,
        rowKind: ENTRY_ROW_KIND,
        entryId: entry?.id ?? null,
        entryKey: key,
        ...fileMetaFromValue(inner, type)
      })
    )
    if (isCompositeType(type)) {
      rows.push(...expandValueToFieldRows(inner, 1, entry?.id ?? null, key))
    }
  }
  syncSheetRowKeyFlags(rows)
  return rows
}

export function createEmptyEntryRow() {
  return makeSheetRow({
    key: '',
    remark: '',
    value: '',
    type: 'string',
    depth: 0,
    rowKind: ENTRY_ROW_KIND,
    entryId: null,
    entryKey: ''
  })
}

export function removeSheetRowAt(rows, index) {
  if (index < 0 || index >= rows.length) return
  const depth = rows[index]._depth ?? 0
  let end = index + 1
  while (end < rows.length && (rows[end]._depth ?? 0) > depth) {
    end++
  }
  rows.splice(index, end - index)
}

export function insertChildRowAfter(rows, parentIndex) {
  const parent = rows[parentIndex]
  if (!parent) return
  const depth = (parent._depth ?? 0) + 1
  let insertAt = parentIndex + 1
  while (insertAt < rows.length && (rows[insertAt]._depth ?? 0) > (parent._depth ?? 0)) {
    insertAt++
  }
  const entryId = parent._entryId ?? null
  const entryKey = parent._entryKey ?? ''
  rows.splice(
    insertAt,
    0,
    makeSheetRow({
      key: '',
      value: '',
      type: 'string',
      depth,
      rowKind: 'field',
      entryId,
      entryKey
    })
  )
  syncSheetRowKeyFlags(rows)
}

function parseKvCellValue(row) {
  const t = String(row?.type || 'string').toLowerCase()
  const raw = row?.value ?? ''
  if (t === 'null') return null
  if (t === 'integer') {
    const n = parseInt(String(raw), 10)
    return Number.isFinite(n) ? n : 0
  }
  if (t === 'number') {
    const n = parseFloat(String(raw))
    return Number.isFinite(n) ? n : 0
  }
  if (t === 'boolean') {
    return String(raw).toLowerCase() === 'true'
  }
  if (t === 'array' || t === 'object') {
    const s = String(raw).trim()
    if (!s) return t === 'array' ? [] : {}
    try {
      return JSON.parse(s)
    } catch {
      return s
    }
  }
  // file：单元格 value 多为存储路径；落库形状为 { type:'file', fileName, storagePath }
  if (t === 'file') {
    const s = String(raw).trim()
    const original = row?._uploadedOriginalName != null ? String(row._uploadedOriginalName).trim() : ''
    if (!s) return { type: 'file', fileName: original, storagePath: '' }
    if (s.startsWith('{')) {
      try {
        const o = JSON.parse(s)
        const merged = { type: 'file', ...o }
        if (original && !merged.fileName) merged.fileName = original
        return merged
      } catch {
        /* fall through */
      }
    }
    return {
      type: 'file',
      fileName: original || fileBaseName(s),
      storagePath: s
    }
  }
  return String(raw)
}

function buildCompositeFromSlice(rows, start, end, compositeType) {
  if (compositeType === 'array') {
    const items = []
    let i = start
    while (i < end) {
      const row = rows[i]
      const rowDepth = row._depth ?? 0
      let j = i + 1
      while (j < end && (rows[j]._depth ?? 0) > rowDepth) {
        j++
      }
      const childType = String(row.type || 'string').toLowerCase()
      let val
      if (isCompositeType(childType)) {
        val = buildCompositeFromSlice(rows, i + 1, j, childType)
      } else {
        val = parseKvCellValue(row)
      }
      items.push(val)
      i = j
    }
    return items
  }

  const out = {}
  let i = start
  while (i < end) {
    const row = rows[i]
    const fieldKey = (row.key || '').trim()
    const rowDepth = row._depth ?? 0
    let j = i + 1
    while (j < end && (rows[j]._depth ?? 0) > rowDepth) {
      j++
    }
    if (fieldKey) {
      const childType = String(row.type || 'string').toLowerCase()
      if (isCompositeType(childType)) {
        out[fieldKey] = buildCompositeFromSlice(rows, i + 1, j, childType)
      } else {
        out[fieldKey] = parseKvCellValue(row)
      }
    }
    i = j
  }
  return out
}

function buildInnerFromEntryRow(entryRow, allRows, childStart, childEnd) {
  const t = String(entryRow.type || 'string').toLowerCase()
  if (!isCompositeType(t)) {
    return parseKvCellValue(entryRow)
  }
  return buildCompositeFromSlice(allRows, childStart, childEnd, t)
}

/**
 * 单表扁平行 → API 保存用条目列表
 */
export function sheetRowsToEntries(rows) {
  const entries = []
  for (const { start, end } of collectEntryBlocks(rows)) {
    const entryRow = rows[start]
    const key = (entryRow.key || '').trim()
    const remark = (entryRow.remark || '').trim()
    const inner = buildInnerFromEntryRow(entryRow, rows, start + 1, end)
    const entryId = entryRow._entryId ?? null
    entries.push({
      id: entryId,
      key,
      remark,
      assets: wrapAssetsPayload(key, inner)
    })
  }
  return entries
}

export function pruneChildrenAfterTypeChange(rows, rowIndex) {
  const row = rows[rowIndex]
  if (!row) return
  const depth = row._depth ?? 0
  let end = rowIndex + 1
  while (end < rows.length && (rows[end]._depth ?? 0) > depth) {
    end++
  }
  if (end > rowIndex + 1) {
    rows.splice(rowIndex + 1, end - rowIndex - 1)
  }
  const t = String(row.type || '').toLowerCase()
  if (isCompositeType(t)) {
    row.value = ''
    insertChildRowAfter(rows, rowIndex)
  } else {
    row.value = row.value ?? ''
    syncSheetRowKeyFlags(rows)
  }
}
