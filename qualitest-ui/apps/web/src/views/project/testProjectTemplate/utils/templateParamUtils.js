/**
 * 模板预制参数：按 kind 分组、参数库 / mention 共用展平，
 * 以及与变量条目扁平行（素材库同形）的互转。
 */
import {
  extractEntryInner,
  parseVariableEntries,
  wrapAssetsPayload,
} from '@/views/project/testProject/utils/variableEntryUtils'
/** @typedef {{ name: string, value?: unknown, remark?: string }} TemplateParamRow */
/** @typedef {{ id?: number|null, key: string, remark?: string, assets: object }} VariableEntry */

/** 将 templateParams 拆成 asset 一组（flow 桶恒为空，供参数库上下文同形）。 */
export function partitionTemplateParams(params) {
  /** @type {{ flow: TemplateParamRow[], asset: TemplateParamRow[] }} */
  const out = { flow: [], asset: [] }
  ;(Array.isArray(params) ? params : []).forEach((row) => {
    if (!row || typeof row !== 'object') return
    const kind = String(row.kind || '').trim()
    const name = String(row.name || '').trim()
    if (!name || kind !== 'asset') return
    out.asset.push({
      name,
      value: row.value,
      remark: row.remark != null ? String(row.remark) : '',
    })
  })
  return out
}

/**
 * 单条 templateParam（asset）→ 变量条目（与项目素材库同形）。
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
 * @param {'asset'} kind
 */
export function templateParamsToVariableEntries(params, kind) {
  const bucket = partitionTemplateParams(params)[kind] || []
  return bucket.map(templateParamRowToVariableEntry).filter(Boolean)
}

/**
 * 变量条目 → templateParam 行。
 * @param {VariableEntry} entry
 * @param {'asset'} kind
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
 * @param {'asset'} kind
 */
export function variableEntriesToTemplateParamRows(entries, kind) {
  return (entries || [])
    .map((e) => variableEntryToTemplateParamRow(e, kind))
    .filter((row) => row.name)
}

/**
 * 用编辑后的 asset 条目重建 templateParams（只保留 asset）。
 * @param {unknown[]} existingParams
 * @param {{ assetEntries?: VariableEntry[] }} parts
 */
export function rebuildTemplateParamsFromVariableEntries(existingParams, parts = {}) {
  return variableEntriesToTemplateParamRows(parts.assetEntries || [], 'asset')
}

/**
 * 规范化预制环境 envVariables 为变量条目数组。
 * @param {unknown} raw
 * @returns {VariableEntry[]}
 */
export function normalizeEnvVariableEntries(raw) {
  if (Array.isArray(raw)) {
    return raw
      .filter((item) => item && typeof item === 'object' && String(item.key || '').trim())
      .map((item) => ({
        id: item.id != null ? item.id : null,
        key: String(item.key).trim(),
        remark: item.remark != null ? String(item.remark) : '',
        assets:
          item.assets && typeof item.assets === 'object'
            ? item.assets
            : wrapAssetsPayload(String(item.key).trim(), ''),
      }))
  }
  if (typeof raw === 'string') {
    return parseVariableEntries(raw)
  }
  return []
}

/**
 * 提交/落库用的 envVariables：去掉 id，只留 key/remark/assets。
 * @param {unknown} raw
 */
export function persistEnvVariableEntries(raw) {
  return normalizeEnvVariableEntries(raw).map((item) => ({
    key: item.key,
    remark: item.remark || '',
    assets: item.assets && typeof item.assets === 'object'
      ? item.assets
      : wrapAssetsPayload(item.key, ''),
  }))
}

/**
 * templateEnvs → 参数库 env 预览行（含 envUrl 合成的 baseUrl）。
 * @param {unknown[]} envs
 */
export function templateEnvsToEnvParamRows(envs) {
  const list = Array.isArray(envs) ? envs : []
  const rows = []
  const seen = new Set()
  for (const env of list) {
    if (!env || typeof env !== 'object') continue
    const url = String(env.envUrl || '').trim()
    if (url && !seen.has('baseUrl')) {
      seen.add('baseUrl')
      rows.push({ name: 'baseUrl', value: url, remark: '环境前置 URL' })
    }
    for (const entry of normalizeEnvVariableEntries(env.envVariables)) {
      const key = String(entry.key || '').trim()
      if (!key || seen.has(key)) continue
      seen.add(key)
      rows.push({
        name: key,
        value: extractEntryInner(entry) ?? '',
        remark: entry.remark != null ? String(entry.remark) : '',
      })
    }
  }
  return rows
}

/**
 * 画布 env 预览：仅来自 templateEnvs。
 * @param {Array<{ name: string, value?: unknown, remark?: string }>} fromEnvs
 */
export function mergeEnvPreviewRows(fromEnvs) {
  const byKey = new Map()
  for (const row of fromEnvs || []) {
    const name = String(row?.name || '').trim()
    if (name) byKey.set(name, row)
  }
  return [...byKey.values()]
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

/** @mention：flow 初值（排除已在 flowSeed 中的键）；预制参数不再提供 flow 行 */
export function templateFlowMentionItems(flowRows, existingSeedKeys = new Set()) {
  return (flowRows || [])
    .filter((row) => row.name && !existingSeedKeys.has(row.name))
    .map((row) => ({
      id: row.name,
      label: `flow.${row.name}`,
      detail: row.remark || '模板 flow 初值',
    }))
}
