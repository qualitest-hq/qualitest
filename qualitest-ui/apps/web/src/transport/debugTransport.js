import axios from 'axios'
import {getToken} from '@/utils/auth'
import {getHttpTransportMode} from '@/transport/runtime'
import {normalizeDebugBodySpec} from '@/transport/debugBodySpec'
import {classifyAxiosOrNetworkError, TransportErrorCode} from '@/transport/errorCodes'
import {mediaMimeFromContentType} from '@/utils/responseMediaPreview'

const debugAxios = axios.create({timeout: 60000, validateStatus: () => true})
// axios 1.x 默认可能给 POST 带 application/json；FormData 必须去掉，否则会被 JSON.stringify
for (const bag of [debugAxios.defaults.headers?.post, debugAxios.defaults.headers?.common]) {
  if (!bag) continue
  delete bag['Content-Type']
  delete bag['content-type']
}

const MAX_RESPONSE_BYTES = 8 * 1024 * 1024

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
          bodyEncoding: raw.bodyEncoding || 'text',
          bodyBase64: raw.bodyBase64 || null,
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
        bodyEncoding: raw.bodyEncoding || 'text',
        bodyBase64: raw.bodyBase64 || null,
        error: null,
        errorCode: null,
        durationMs: dt
      }
    } catch (e) {
      const dt = Math.round(performance.now() - t0)
      const {code, message, corsHint} = classifyAxiosOrNetworkError(e)
      return emptyError(dt, code, message, corsHint)
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
        bodyEncoding: 'text',
        bodyBase64: null,
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
          bodyEncoding: 'text',
          bodyBase64: null,
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
          bodyEncoding: 'text',
          bodyBase64: null,
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
          bodyEncoding: body.bodyEncoding || 'text',
          bodyBase64: body.bodyBase64 || null,
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
        bodyEncoding: 'text',
        bodyBase64: null,
        error: res.status >= 400 ? `转发服务返回 ${res.status}` : null,
        errorCode: res.status >= 400 ? TransportErrorCode.HTTP : null,
        durationMs: dt
      }
    } catch (e) {
      const dt = Math.round(performance.now() - t0)
      const {code, message, corsHint} = classifyAxiosOrNetworkError(e)
      return emptyError(dt, code, message, corsHint)
    }
  }

  try {
    // FormData 时勿带 Content-Type，交由浏览器写 multipart boundary
    const reqHeaders = {...(headers || {})}
    const payload = ['GET', 'HEAD'].includes(String(method).toUpperCase()) ? undefined : data
    const isFormData = typeof FormData !== 'undefined' && payload instanceof FormData
    if (isFormData) {
      for (const hk of Object.keys(reqHeaders)) {
        if (hk.toLowerCase() === 'content-type') delete reqHeaders[hk]
      }
      // 显式 false：阻止 axios 用默认 application/json 把 FormData 序列化成 "{}" / 字段 JSON
      reqHeaders['Content-Type'] = false
    }
    // arraybuffer：按响应 CT 区分文本与媒体，避免裸 image/* 被 UTF-8 损坏
    const res = await debugAxios.request({
      url: fullUrl,
      method,
      headers: reqHeaders,
      data: payload,
      responseType: 'arraybuffer',
      validateStatus: () => true
    })
    const dt = Math.round(performance.now() - t0)
    const flatHeaders = flattenAxiosHeaders(res.headers)
    const contentType = headerValue(flatHeaders, 'content-type')
    const preview = decodeArrayBufferPreview(res.data, contentType)
    return {
      ok: res.status >= 200 && res.status < 300,
      status: res.status,
      statusText: res.statusText || '',
      headers: flatHeaders,
      bodyText: preview.bodyText,
      bodyEncoding: preview.bodyEncoding,
      bodyBase64: preview.bodyBase64,
      error: null,
      errorCode: null,
      durationMs: dt
    }
  } catch (e) {
    const dt = Math.round(performance.now() - t0)
    const {code, message, corsHint} = classifyAxiosOrNetworkError(e)
    return emptyError(dt, code, message, corsHint)
  }
}

export function corsHintText() {
  return '浏览器受 CORS 限制：可请目标服务开启跨域、配置同源反向代理、使用质衡桌面端直连，或启用 Java 服务端转发。'
}

function emptyError(dt, code, message, corsHint) {
  return {
    ok: false,
    status: null,
    statusText: '',
    headers: {},
    bodyText: '',
    bodyEncoding: 'text',
    bodyBase64: null,
    error: message,
    errorCode: code,
    durationMs: dt,
    corsHint: corsHint ? corsHintText() : undefined
  }
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

function flattenAxiosHeaders(headers) {
  const out = {}
  if (!headers || typeof headers !== 'object') {
    return out
  }
  for (const [k, v] of Object.entries(headers)) {
    if (v == null) continue
    out[k] = Array.isArray(v) ? v.join(', ') : String(v)
  }
  return out
}

function headerValue(headers, name) {
  const want = String(name).toLowerCase()
  for (const [k, v] of Object.entries(headers || {})) {
    if (k && k.toLowerCase() === want) {
      return String(v)
    }
  }
  return ''
}

/**
 * @param {ArrayBuffer|Uint8Array|null|undefined} data
 * @param {string} contentType
 */
function decodeArrayBufferPreview(data, contentType) {
  const bytes = toUint8Array(data)
  const originalLength = bytes.length
  const truncated = originalLength > MAX_RESPONSE_BYTES
  const slice = truncated ? bytes.subarray(0, MAX_RESPONSE_BYTES) : bytes
  const mime = mediaMimeFromContentType(contentType)

  if (mime) {
    let bodyText = `[binary ${mime} · ${originalLength} bytes]`
    if (truncated) {
      bodyText += `\n\n… 响应体已截断（>${MAX_RESPONSE_BYTES} 字节）`
    }
    return {
      bodyText,
      bodyEncoding: 'base64',
      bodyBase64: bytesToBase64(slice)
    }
  }

  let text = new TextDecoder('utf-8').decode(slice)
  // JSON 时 pretty-print，便于调试与媒体字段识别
  const trimmed = text.trim()
  if (
    (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
    (trimmed.startsWith('[') && trimmed.endsWith(']'))
  ) {
    try {
      text = JSON.stringify(JSON.parse(trimmed), null, 2)
    } catch {
      /* keep raw */
    }
  }
  if (truncated) {
    text += `\n\n… 响应体已截断（>${MAX_RESPONSE_BYTES} 字节）`
  }
  return {bodyText: text, bodyEncoding: 'text', bodyBase64: null}
}

function toUint8Array(data) {
  if (!data) {
    return new Uint8Array(0)
  }
  if (data instanceof Uint8Array) {
    return data
  }
  if (data instanceof ArrayBuffer) {
    return new Uint8Array(data)
  }
  return new Uint8Array(0)
}

function bytesToBase64(bytes) {
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

async function buildJavaForwardPayload(built) {
  const body = await normalizeDebugBodySpec(built.method, built.data)
  const payload = {
    method: built.method,
    url: built.fullUrl,
    headers: headersToPairs(built.headers),
    timeoutMs: 60000,
    followRedirects: true,
    allowInsecureTls: false,
    correlationId: typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : undefined,
    body
  }
  const apiId = built?.testProjectApiId
  if (apiId != null && String(apiId).trim() !== '') {
    const n = Number(apiId)
    if (Number.isFinite(n)) {
      payload.testProjectApiId = n
    }
  }
  return payload
}
