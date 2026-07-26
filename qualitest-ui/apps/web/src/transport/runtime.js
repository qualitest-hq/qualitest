/**
 * 当前 HTTP 传输模式（设计 §9）
 * - electron-main：由 preload 注入，治理与调试均走主进程
 * - browser-java-forward：调试走同源 Java 转发（需后端）
 * - browser：默认 Web（治理走 Vite 代理 + axios）
 */

/** @typedef {'browser' | 'browser-java-forward' | 'electron-main'} HttpTransportMode */

/**
 * @param {HttpTransportMode} [override] 页面运行时覆盖（Web 调试页切换）
 * @returns {HttpTransportMode}
 */
export function getHttpTransportMode(override) {
  if (typeof window !== 'undefined' && window.__QUALITEST_ELECTRON__?.transport === 'electron-main') {
    return 'electron-main'
  }
  if (override === 'browser' || override === 'browser-java-forward') {
    return override
  }
  const v = import.meta.env.VITE_QUALITEST_HTTP_TRANSPORT
  if (v === 'browser-java-forward') {
    return 'browser-java-forward'
  }
  return 'browser'
}

export function isElectronMainTransport() {
  return getHttpTransportMode() === 'electron-main'
}

/** Web 端是否可展示调试传输模式切换 */
export function isWebDebugTransportSwitchable() {
  return typeof window !== 'undefined' && !window.__QUALITEST_ELECTRON__?.transport
}

/**
 * Java 转发接口是否已配置（不探测网络）
 * @returns {{ available: boolean, forwardPath?: string, reason?: string }}
 */
export function getHttpForwardAvailability() {
  const forwardPath = import.meta.env.VITE_HTTP_FORWARD_API
  if (!forwardPath || String(forwardPath).trim() === '') {
    return {available: false, reason: '未配置 VITE_HTTP_FORWARD_API'}
  }
  return {available: true, forwardPath: String(forwardPath).trim()}
}
