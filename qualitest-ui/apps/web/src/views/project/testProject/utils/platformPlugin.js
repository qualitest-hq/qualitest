/**
 * 环境插件绑定辅助（通用，不绑死官方 plugin id）。
 */

/**
 * 绑定列表中是否已启用指定插件（enabled 为 1 视为启用）。
 * @param {Array<{ pluginId?: string, enabled?: number|string }>|null|undefined} rows
 * @param {string} pluginId
 */
export function isPluginEnabled(rows, pluginId) {
  if (!Array.isArray(rows) || !pluginId) return false
  return rows.some((r) => r?.pluginId === pluginId && Number(r.enabled) === 1)
}

/**
 * 取已启用绑定行；未启用返回 undefined。
 * @param {Array<{ pluginId?: string, enabled?: number|string }>|null|undefined} rows
 * @param {string} pluginId
 */
export function findEnabledBinding(rows, pluginId) {
  if (!Array.isArray(rows) || !pluginId) return undefined
  return rows.find((r) => r?.pluginId === pluginId && Number(r.enabled) === 1)
}

/**
 * 已启用插件列表中是否声明了指定 hook（如 onCheckpoint）。
 * @param {Array<{ enabled?: boolean, hooks?: Array<{ name?: string }> }>|null|undefined} envPlugins
 * @param {string} hookName
 */
export function hasEnabledHook(envPlugins, hookName) {
  if (!Array.isArray(envPlugins) || !hookName) return false
  return envPlugins.some(
    (p) =>
      !!p?.enabled &&
      Array.isArray(p.hooks) &&
      p.hooks.some((h) => h?.name === hookName),
  )
}
