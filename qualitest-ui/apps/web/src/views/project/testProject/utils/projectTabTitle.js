/**
 * 生成项目相关页面的 TagsView 标题：项目名 + 功能名。
 *
 * @param {string} projectName 测试项目名称
 * @param {string} feature 功能标识（如 API、测试流、测试流名称）
 * @returns {string}
 */
export function buildProjectTabTitle(projectName, feature) {
  const name = String(projectName ?? '').trim()
  const feat = String(feature ?? '').trim()
  if (!name) return feat || '未命名项目'
  if (!feat) return name
  return `${name}·${feat}`
}
