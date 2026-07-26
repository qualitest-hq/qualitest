import axios from 'axios'
import {getToken} from '@/utils/auth'
import {getHttpTransportMode} from '@/transport/runtime'
import {normalizeDebugBodySpec} from '@/transport/debugBodySpec'
import {classifyAxiosOrNetworkError, TransportErrorCode} from '@/transport/errorCodes'

const debugAxios = axios.create({timeout: 60000, validateStatus: () => true})

/**
 * @param {object} built
 * @param {string} built.fullUrl
 * @param {string} built.method
 * @param {Record<string, string>} built.headers
 * @param {unknown} [built.data]
 * @param {{ transportMode?: 'browser' | 'browser-java-forward' }} [options]
 */
export async function executeDebugRequest(built, options) {
  const {fullUrl, method, headers, data} = built
  const t0 = performance.now()
  const mode = getHttpTransportMode(options?.transportMode)

  if (mode === 'electron-main' && window.__QUALITEST_ELECTRON__?.debugHttpRequest) {
    try {
      const bodySpec = await normalizeDebugBodySpec(method, data)
      const raw = await window.__QUALITEST_ELECTRON__.debugHttpRequest({
        method,
        url: fullUrl,
        headers: headersToPairs(headers),
        bodySpec,
        timeoutMs: 60000,
        followRedirects: true,
        insecureTls: false,
        correlationId: typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : undefined
      })
      const dt = Math.round(performance.now() - t0)
      if (raw?.error) {
        return {
          ok: false,
          status: raw.status ?? null,
          statusText: raw.statusText || '',
          headers: raw.headers || {},
          bodyText: raw.bodyText || '',
          error: raw.error,
          errorCode: raw.errorCode || TransportErrorCode.UNKNOWN,
          durationMs: dt,
          corsHint: raw.corsHint
        }
      }
      return {
        ok: (raw.status ?? 0) >= 200 && (raw.status ?? 0) < 300,
        status: raw.status,
        statusText: raw.statusText || '',
        headers: raw.headers || {},
        bodyText: raw.bodyText ?? '',
        error: null,
        errorCode: null,
        durationMs: dt
      }
    } catch (e) {
      const dt = Math.round(performance.now() - t0)
      const {code, message, corsHint} = classifyAxiosOrNetworkError(e)
      return {
        ok: false,
        status: null,
        statusText: '',
        headers: {},
        bodyText: '',
        error: message,
        errorCode: code,
        durationMs: dt,
        corsHint: corsHint ? corsHintText() : undefined
      }
    }
  }

  if (mode === 'browser-java-forward') {
    const forwardPath = import.meta.env.VITE_HTTP_FORWARD_API
    if (!forwardPath || String(forwardPath).trim() === '') {
      const dt = Math.round(performance.now() - t0)
      return {
        ok: false,
        status: null,
        statusText: '',
        headers: {},
        bodyText: '',
        error: '未配置 VITE_HTTP_FORWARD_API，无法使用服务端转发模式',
        errorCode: TransportErrorCode.POLICY,
        durationMs: dt
      }
    }
    try {
      const payload = await buildJavaForwardPayload(built)
      const reqHeaders = {'Content-Type': 'application/json'}
      const token = getToken()
      if (token) {
        reqHeaders.Authorization = 'Bearer ' + token
      }
      const res = await debugAxios.post(forwardPath, payload, {headers: reqHeaders})
      const dt = Math.round(performance.now() - t0)
      if (res.status === 401 || res.status === 403) {
        return {
          ok: false,
          status: res.status,
          statusText: res.statusText || '',
          headers: {},
          bodyText: '',
          error: '转发接口需要登录，请重新登录后再试',
          errorCode: TransportErrorCode.POLICY,
          durationMs: dt
        }
      }
      if (res.status === 404 || res.status === 501) {
        return {
          ok: false,
          status: res.status,
          statusText: res.statusText || '',
          headers: {},
          bodyText: '',
          error: 'Java 调试转发接口尚未就绪或未发布',
          errorCode: TransportErrorCode.FORWARD_NOT_READY,
          durationMs: dt
        }
      }
      const body = res.data
      if (body && typeof body === 'object' && (body.forwarded === true || body.forwarded === false)) {
        const policyBlocked = body.forwarded === false
        const targetStatus = body.status ?? 0
        return {
          ok: !policyBlocked && !body.error && targetStatus >= 200 && targetStatus < 300,
          status: body.status ?? res.status,
          statusText: body.statusText || '',
          headers: body.responseHeaders || {},
          bodyText: typeof body.bodyText === 'string' ? body.bodyText : stringifyBody(body.body),
          error: body.error || null,
          errorCode: body.errorCode || null,
          durationMs: dt
        }
      }
      let bodyText = res.data
      if (typeof bodyText === 'object' && bodyText !== null) {
        bodyText = JSON.stringify(bodyText, null, 2)
      } else {
        bodyText = bodyText != null ? String(bodyText) : ''
      }
      return {
        ok: res.status >= 200 && res.status < 300,
        status: res.status,
        statusText: res.statusText || '',
        headers: res.headers || {},
        bodyText,
        error: res.status >= 400 ? `转发服务返回 ${res.status}` : null,
        errorCode: res.status >= 400 ? TransportErrorCode.HTTP : null,
        durationMs: dt
      }
    } catch (e) {
      const dt = Math.round(performance.now() - t0)
      const {code, message, corsHint} = classifyAxiosOrNetworkError(e)
      return {
        ok: false,
        status: null,
        statusText: '',
        headers: {},
        bodyText: '',
        error: message,
        errorCode: code,
        durationMs: dt,
        corsHint: corsHint ? corsHintText() : undefined
      }
    }
  }

  try {
    const res = await debugAxios.request({
      url: fullUrl,
      method,
      headers,
      data: ['GET', 'HEAD'].includes(String(method).toUpperCase()) ? undefined : data,
      validateStatus: () => true
    })
    const dt = Math.round(performance.now() - t0)
    let bodyText = res.data
    if (typeof bodyText === 'object' && bodyText !== null) {
      try {
        bodyText = JSON.stringify(bodyText, null, 2)
      } catch {
        bodyText = String(bodyText)
      }
    } else {
      bodyText = bodyText != null ? String(bodyText) : ''
    }
    return {
      ok: res.status >= 200 && res.status < 300,
      status: res.status,
      statusText: res.statusText || '',
      headers: res.headers || {},
      bodyText,
      error: null,
      errorCode: null,
      durationMs: dt
    }
  } catch (e) {
    const dt = Math.round(performance.now() - t0)
    const {code, message, corsHint} = classifyAxiosOrNetworkError(e)
    return {
      ok: false,
      status: null,
      statusText: '',
      headers: {},
      bodyText: '',
      error: message,
      errorCode: code,
      durationMs: dt,
      corsHint: corsHint ? corsHintText() : undefined
    }
  }
}

export function corsHintText() {
  return '浏览器受 CORS 限制：可请目标服务开启跨域、配置同源反向代理、使用质衡桌面端直连，或启用 Java 服务端转发。'
}

function headersToPairs(h) {
  const out = []
  if (!h || typeof h !== 'object') {
    return out
  }
  for (const [k, v] of Object.entries(h)) {
    if (v == null) {
      continue
    }
    out.push({name: k, value: String(v)})
  }
  return out
}

function stringifyBody(b) {
  if (b == null) {
    return ''
  }
  if (typeof b === 'string') {
    return b
  }
  try {
    return JSON.stringify(b, null, 2)
  } catch {
    return String(b)
  }
}

async function buildJavaForwardPayload(built) {
  const body = await normalizeDebugBodySpec(built.method, built.data)
  return {
    method: built.method,
    url: built.fullUrl,
    headers: headersToPairs(built.headers),
    timeoutMs: 60000,
    followRedirects: true,
    allowInsecureTls: false,
    correlationId: typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : undefined,
    body
  }
}
