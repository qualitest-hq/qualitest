/**
 * 模板预制参数：按 kind 分组、flowSeed 合并、参数库 / mention 共用展平，
 * 以及与变量条目扁平行（素材库同形）的互转。
 */

import {
  extractEntryInner,
  wrapAssetsPayload,
} from '@/views/project/testProject/utils/variableEntryUtils'

/** @typedef {{ name: string, value?: unknown, remark?: string }} TemplateParamRow */
/** @typedef {{ id?: number|null, key: string, remark?: string, assets: object }} VariableEntry */

/** 将 templateParams 拆成 flow / env / asset 三组（与 store.templateParamContext 同形）。 */
export function partitionTemplateParams(params) {
  /** @type {{ flow: TemplateParamRow[], env: TemplateParamRow[], asset: TemplateParamRow[] }} */
  const out = { flow: [], env: [], asset: [] }
  ;(Array.isArray(params) ? params : []).forEach((row) => {
    if (!row || typeof row !== 'object') return
    const kind = String(row.kind || '').trim()
    const name = String(row.name || '').trim()
    if (!name) return
    const item = {
      name,
      value: row.value,
      remark: row.remark != null ? String(row.remark) : '',
    }
    if (kind === 'flow') out.flow.push(item)
    else if (kind === 'env') out.env.push(item)
    else if (kind === 'asset') out.asset.push(item)
  })
  return out
}

/**
 * 单条 templateParam（env/asset）→ 变量条目（与项目素材库同形）。
 * @param {TemplateParamRow} row
 * @returns {VariableEntry | null}
 */
export function templateParamRowToVariableEntry(row) {
  const key = String(row?.name || '').trim()
  if (!key) return null
  return {
    id: null,
    key,
    remark: row.remark != null ? String(row.remark) : '',
    assets: wrapAssetsPayload(key, row.value != null ? row.value : ''),
  }
}

/**
 * templateParams 中指定 kind → 变量条目列表（供 entriesToSheetRows）。
 * @param {unknown[]} params
 * @param {'env'|'asset'} kind
 */
export function templateParamsToVariableEntries(params, kind) {
  const bucket = partitionTemplateParams(params)[kind] || []
  return bucket.map(templateParamRowToVariableEntry).filter(Boolean)
}

/**
 * 变量条目 → templateParam 行。
 * @param {VariableEntry} entry
 * @param {'env'|'asset'} kind
 */
export function variableEntryToTemplateParamRow(entry, kind) {
  const name = String(entry?.key || '').trim()
  return {
    kind,
    name,
    value: extractEntryInner(entry) ?? '',
    remark: entry?.remark != null ? String(entry.remark) : '',
  }
}

/**
 * 变量条目列表 → templateParam 行列表（丢掉无 key）。
 * @param {VariableEntry[]} entries
 * @param {'env'|'asset'} kind
 */
export function variableEntriesToTemplateParamRows(entries, kind) {
  return (entries || [])
    .map((e) => variableEntryToTemplateParamRow(e, kind))
    .filter((row) => row.name)
}

/**
 * 合并写出 templateParams：保留存量 flow，再接 env / asset。
 * @param {unknown[]} existingParams
 * @param {{ envEntries?: VariableEntry[], assetEntries?: VariableEntry[] }} parts
 */
export function rebuildTemplateParamsFromVariableEntries(existingParams, parts = {}) {
  const { flow } = partitionTemplateParams(existingParams)
  const next = flow.map((row) => ({
    kind: 'flow',
    name: row.name,
    value: row.value ?? '',
    remark: row.remark || '',
  }))
  next.push(...variableEntriesToTemplateParamRows(parts.envEntries || [], 'env'))
  next.push(...variableEntriesToTemplateParamRows(parts.assetEntries || [], 'asset'))
  return next
}

/**
 * 把模板 flow 初值灌入 flowSeed（同名键不覆盖）。
 * @param {Record<string, unknown>} seed
 * @param {TemplateParamRow[]} flowRows
 */
export function mergeFlowSeedFromTemplateParams(seed, flowRows) {
  const next = { ...(seed || {}) }
  let changed = false
  ;(flowRows || []).forEach((row) => {
    const name = String(row?.name || '').trim()
    if (!name || Object.prototype.hasOwnProperty.call(next, name)) return
    next[name] = row.value != null ? row.value : ''
    changed = true
  })
  return { seed: next, changed }
}

/** 参数库：asset 行 → { key, remark, assets } 列表 */
export function templateAssetParamEntries(assetRows) {
  return (assetRows || []).map((row) => {
    const key = String(row.name || '').trim()
    const v = row.value
    return {
      key,
      remark: row.remark || key,
      assets: wrapAssetsPayload(key, v != null ? v : ''),
    }
  })
}

/** @mention：asset 候选项 */
export function templateAssetMentionItems(assetRows) {
  return (assetRows || []).flatMap((row) => {
    const v = row.value
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      return Object.keys(v).map((key) => ({
        id: `${row.name}.${key}`,
        label: `asset.${row.name}.${key}`,
        detail: row.remark || '模板 asset',
      }))
    }
    return [{
      id: row.name,
      label: `asset.${row.name}`,
      detail: row.remark || '模板 asset',
    }]
  })
}

/** @mention：flow 初值（排除已在 flowSeed 中的键） */
export function templateFlowMentionItems(flowRows, existingSeedKeys = new Set()) {
  return (flowRows || [])
    .filter((row) => row.name && !existingSeedKeys.has(row.name))
    .map((row) => ({
      id: row.name,
      label: `flow.${row.name}`,
      detail: row.remark || '模板 flow 初值',
    }))
}
