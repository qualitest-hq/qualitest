/** 根据环境名启发式判定是否为生产环境（无 envType 字段时的护栏） */
export function isProductionEnvName(envName) {
  const name = String(envName ?? '').trim()
  if (!name) return false
  return /prod|生产|production/i.test(name)
}
