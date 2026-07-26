import {
  DEFAULT_BACKEND_BASE_URL,
  resolveInitialBackendBaseUrl,
  saveBackendBaseUrl
} from './backend-config.js'
import {promptBackendBaseUrl} from './backend-setup-dialog.js'
import {probeBackend} from '../http/probe.js'
import {setRuntimeBackendBaseUrl} from './backend-runtime.js'

function formatProbeError(url) {
  return `无法连接到后端：${url}。请确认质衡服务已启动，或在下方填写正确地址。`
}

/**
 * 启动时探测后端；失败则弹窗直至连通或用户退出
 * @returns {Promise<string|null>} 连通后的 base URL；用户取消则 null
 */
export async function ensureBackendAvailable() {
  let candidate = resolveInitialBackendBaseUrl()

  while (true) {
    const ok = await probeBackend(candidate)
    if (ok) {
      const saved = saveBackendBaseUrl(candidate)
      setRuntimeBackendBaseUrl(saved)
      console.log('[qualitest-desktop] 后端已连接:', saved)
      return saved
    }

    console.warn('[qualitest-desktop] 后端不可达:', candidate)
    const next = await promptBackendBaseUrl(
      candidate,
      formatProbeError(candidate)
    )
    if (next == null) {
      return null
    }
    candidate = next
  }
}

export {DEFAULT_BACKEND_BASE_URL}
