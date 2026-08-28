/**
 * 模板预制参数：按 kind 分组、flowSeed 合并、参数库 / mention 共用展平。
 */

/** @typedef {{ name: string, value?: unknown, remark?: string }} TemplateParamRow */

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
    const v = row.value
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      return { key: row.name, remark: row.remark || row.name, assets: v }
    }
    return {
      key: row.name,
      remark: row.remark || row.name,
      assets: { [row.name]: v ?? '' },
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
