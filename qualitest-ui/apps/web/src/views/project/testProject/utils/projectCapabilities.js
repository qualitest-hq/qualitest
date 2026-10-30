/**
 * 项目成员细粒度权限：解析 myProjectContext 的 capabilities，并提供 has 判断。
 */

/**
 * 从「当前用户项目上下文」接口的 data 中取出权限 code 列表。
 * 无 capabilities 字段或非数组时返回空数组。
 * @param {unknown} data 接口 data 载荷
 * @returns {string[]}
 */
export function parseProjectCapabilities(data) {
  return Array.isArray(data?.capabilities) ? data.capabilities : []
}

/**
 * 是否具备指定项目权限。
 * @param {string[]|Set<string>|null|undefined} capabilities
 * @param {string} code
 * @returns {boolean}
 */
export function hasProjectCapability(capabilities, code) {
  if (!code) return false
  if (capabilities instanceof Set) return capabilities.has(code)
  return Array.isArray(capabilities) && capabilities.includes(code)
}

/**
 * 是否具备任一指定项目权限。
 * @param {string[]|Set<string>|null|undefined} capabilities
 * @param {string[]} codes
 * @returns {boolean}
 */
export function hasAnyProjectCapability(capabilities, codes) {
  if (!Array.isArray(codes) || !codes.length) return false
  return codes.some((code) => hasProjectCapability(capabilities, code))
}
