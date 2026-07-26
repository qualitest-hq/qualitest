/** 与主进程 / Java 转发对齐的稳定错误分类（设计 §10.7） */
export const TransportErrorCode = {
  TIMEOUT: 'TIMEOUT',
  DNS: 'DNS',
  TLS: 'TLS',
  CORS: 'CORS',
  NETWORK: 'NETWORK',
  HTTP: 'HTTP',
  POLICY: 'POLICY',
  FORWARD_NOT_READY: 'FORWARD_NOT_READY',
  UNKNOWN: 'UNKNOWN'
}

/** Electron IPC channel（preload 与 main 须一致） */
export const IpcChannel = {
  GOVERNANCE: 'qualitest:governance',
  DEBUG_HTTP: 'qualitest:debugHttp',
  BACKEND_GET_CONFIG: 'qualitest:backendGetConfig',
  BACKEND_SETUP_INITIAL: 'qualitest:backendSetupInitial',
  BACKEND_SETUP_SUBMIT: 'qualitest:backendSetupSubmit',
  BACKEND_SETUP_CANCEL: 'qualitest:backendSetupCancel'
}

/**
 * @param {unknown} err
 * @returns {{ code: string, message: string, corsHint?: boolean }}
 */
export function classifyAxiosOrNetworkError(err) {
  const msg = err && typeof err === 'object' && 'message' in err ? String(err.message) : String(err || '')
  const lower = msg.toLowerCase()

  if (lower.includes('timeout') || lower.includes('timed out')) {
    return {code: TransportErrorCode.TIMEOUT, message: msg}
  }
  if (lower.includes('network error') || lower.includes('err_network')) {
    return {code: TransportErrorCode.NETWORK, message: msg}
  }
  if (lower.includes('cors') || lower.includes('cross-origin') || lower.includes('access-control')) {
    return {code: TransportErrorCode.CORS, message: msg, corsHint: true}
  }
  if (lower.includes('certificate') || lower.includes('ssl') || lower.includes('tls')) {
    return {code: TransportErrorCode.TLS, message: msg}
  }
  if (lower.includes('getaddrinfo') || lower.includes('enotfound') || lower.includes('nxdomain')) {
    return {code: TransportErrorCode.DNS, message: msg}
  }
  return {code: TransportErrorCode.UNKNOWN, message: msg}
}

/**
 * Node/Electron 主进程网络错误分类（与 TransportErrorCode 对齐）
 * @param {string} msg
 * @returns {string}
 */
export function classifyNodeNetError(msg) {
  const m = String(msg || '').toLowerCase()
  if (m.includes('timeout')) {
    return TransportErrorCode.TIMEOUT
  }
  if (m.includes('certificate') || m.includes('ssl') || m.includes('tls')) {
    return TransportErrorCode.TLS
  }
  if (m.includes('getaddrinfo') || m.includes('enotfound')) {
    return TransportErrorCode.DNS
  }
  return TransportErrorCode.NETWORK
}
