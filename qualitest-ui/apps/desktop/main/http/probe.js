import axios from 'axios'

/**
 * 探测质衡后端是否可达（与登录页验证码接口一致，无需鉴权）
 * @param {string} backendBaseUrl
 * @param {number} [timeoutMs]
 * @returns {Promise<boolean>}
 */
export async function probeBackend(backendBaseUrl, timeoutMs = 5000) {
  const base = String(backendBaseUrl || '').replace(/\/$/, '')
  if (!base) {
    return false
  }
  const url = `${base}/captchaImage`
  try {
    const res = await axios.get(url, {
      timeout: timeoutMs,
      validateStatus: () => true,
      proxy: false
    })
    if (res.status !== 200) {
      return false
    }
    const data = res.data
    return data != null && typeof data === 'object'
  } catch {
    return false
  }
}
