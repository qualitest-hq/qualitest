import request from '@/utils/request'

/**
 * 查询已登记的插件包列表。
 */
export function listPluginPackages() {
  return request({
    url: '/project/pluginHost/packages',
    method: 'get',
  })
}

/**
 * 上传 zip，在质衡本机解压安装并登记插件包。
 * 字段名 file；关闭防重复提交；超时 60 秒。
 * 只装质衡宿主侧，不装被测机 CLI。
 *
 * @param {File|Blob} file 插件 zip 文件
 */
export function installPluginPackage(file) {
  const form = new FormData()
  form.append('file', file)
  return request({
    url: '/project/pluginHost/install',
    method: 'post',
    data: form,
    timeout: 60000,
    headers: {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false,
    },
  })
}

/**
 * 查询质衡本机当前插件 zip 安装根目录。
 */
export function getPluginInstallDir() {
  return request({
    url: '/project/pluginHost/installDir',
    method: 'get',
  })
}

/**
 * 查询指定环境下的插件绑定列表。
 *
 * @param {string|number} envId 环境 ID
 */
export function listEnvPluginBindings(envId) {
  return request({
    url: `/project/pluginHost/env/${envId}/bindings`,
    method: 'get',
  })
}

/**
 * 在指定环境启用插件。
 *
 * @param {string|number} envId 环境 ID
 * @param {{ pluginId: string, configJson?: string }} data 启用参数
 */
export function enableEnvPlugin(envId, data) {
  return request({
    url: `/project/pluginHost/env/${envId}/enable`,
    method: 'post',
    data,
  })
}

/**
 * 在指定环境停用插件。
 *
 * @param {string|number} envId 环境 ID
 * @param {{ pluginId: string }} data 停用参数
 */
export function disableEnvPlugin(envId, data) {
  return request({
    url: `/project/pluginHost/env/${envId}/disable`,
    method: 'post',
    data,
  })
}

/**
 * 查询环境已启用插件的前端投影（如画布顶栏徽章）。
 *
 * @param {string|number} envId 环境 ID
 */
export function getEnvPluginUi(envId) {
  return request({
    url: `/project/pluginHost/env/${envId}/ui`,
    method: 'get',
  })
}

/**
 * 读取项目对某插件的挂载配置（如库存档点的 bindAlias）。
 *
 * @param {string|number} projectId 项目 ID
 * @param {string} pluginId 插件标识
 */
export function getProjectPluginMount(projectId, pluginId) {
  return request({
    url: `/project/pluginHost/project/${projectId}/mount`,
    method: 'get',
    params: { pluginId },
  })
}

/**
 * 保存项目对某插件的挂载配置。
 *
 * @param {string|number} projectId 项目 ID
 * @param {{ pluginId: string, bindAlias?: string, storeKeys?: string[], mountJson?: object }} data 挂载内容
 */
export function saveProjectPluginMount(projectId, data) {
  return request({
    url: `/project/pluginHost/project/${projectId}/mount`,
    method: 'put',
    data,
  })
}

/**
 * 列出项目下全部插件挂载。
 *
 * @param {string|number} projectId 项目 ID
 */
export function listProjectPluginMounts(projectId) {
  return request({
    url: `/project/pluginHost/project/${projectId}/mounts`,
    method: 'get',
  })
}

/**
 * 拼插件 ui 静态资源 URL（同源，由前端带鉴权 fetch）。
 *
 * @param {string} pluginId
 * @param {string} entry 相对路径，如 ui/env-panel.js
 */
export function getEnvPluginUiAssetUrl(pluginId, entry) {
  const rel = String(entry || '').replace(/^\/+/, '')
  return `/project/pluginHost/ui/${encodeURIComponent(pluginId)}/${rel}`
}

/**
 * 在指定环境调用已启用插件的一项能力（如 list、archive.delete、seed.apply）。
 *
 * @param {string|number} envId 环境 ID
 * @param {{ pluginId: string, capability: string, args?: Record<string, unknown>, runId?: string|number }} data 调用参数
 */
export function invokeEnvPlugin(envId, data) {
  return request({
    url: `/project/pluginHost/env/${envId}/invoke`,
    method: 'post',
    data,
  })
}

/**
 * 探活当前环境执行代理。
 *
 * @param {string|number} envId 环境 ID
 */
export function pingSutPushAgent(envId) {
  return request({
    url: `/project/pluginHost/env/${envId}/sut-push/ping`,
    method: 'get',
  })
}

/**
 * 经执行代理推送安装业务 CLI（如 sdap）。
 *
 * @param {string|number} envId 环境 ID
 * @param {{ productId?: string, artifactPublicBaseUrl?: string }} [data]
 */
export function sutPushInstall(envId, data = {}) {
  return request({
    url: `/project/pluginHost/env/${envId}/sut-push/install`,
    method: 'post',
    data,
    timeout: 360000,
  })
}

/**
 * 经执行代理写入被测机 bind。
 *
 * @param {string|number} envId 环境 ID
 * @param {{ productId?: string, bindAlias?: string, body?: object }} [data]
 */
export function sutPushBind(envId, data = {}) {
  return request({
    url: `/project/pluginHost/env/${envId}/sut-push/bind`,
    method: 'post',
    data,
    timeout: 60000,
  })
}
