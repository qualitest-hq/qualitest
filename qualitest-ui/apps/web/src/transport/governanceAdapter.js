import {isElectronMainTransport} from '@/transport/runtime'

/**
 * Electron 主进程治理 HTTP；在装配后作为 axios 自定义 adapter 使用。
 * @param {import('axios').InternalAxiosRequestConfig} config
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export async function governanceElectronAdapter(config) {
  const api = typeof window !== 'undefined' ? window.__QUALITEST_ELECTRON__ : null
  if (!api?.governanceRequest) {
    throw new Error('Electron 桥未就绪：缺少 governanceRequest')
  }
  const res = await api.governanceRequest({
    method: config.method || 'get',
    url: config.url || '',
    params: config.params,
    data: config.data,
    headers: {...(config.headers || {})},
    responseType: config.responseType,
    timeout: config.timeout,
    devApiPrefix: import.meta.env.VITE_APP_BASE_API || '/dev-api'
  })

  if (res.error || res.status === 0) {
    const err = new Error(res.error || '主进程请求失败')
    return Promise.reject(err)
  }

  let data = res.data
  if (res.__binary && res.data != null) {
    const u8 = res.data instanceof Uint8Array ? res.data : new Uint8Array(res.data)
    data = new Blob([u8])
  }

  return {
    data,
    status: res.status,
    statusText: res.statusText || '',
    headers: res.headers,
    config,
    request: {}
  }
}

/**
 * @param {import('axios').AxiosInstance} service
 */
export function attachGovernanceAdapterIfElectron(service) {
  if (!isElectronMainTransport()) {
    return
  }
  service.defaults.adapter = governanceElectronAdapter
}
