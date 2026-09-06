import axios from 'axios'
import FormData from 'form-data'
import https from 'node:https'
import {TransportErrorCode, classifyNodeNetError, mediaMimeFromContentType} from '@qualitest/transport-types'

/**
 * @param {string} backendBase
 * @param {string} devApiPrefix e.g. /dev-api
 * @param {string} requestUrl path+query from axios after interceptors
 */
export function resolveGovernanceUrl(backendBase, devApiPrefix, requestUrl) {
  const base = backendBase.replace(/\/$/, '')
  const prefix = (devApiPrefix || '/dev-api').replace(/\/$/, '')
  let p = requestUrl || ''
  if (p.startsWith(prefix)) {
    p = p.slice(prefix.length)
  }
  if (!p.startsWith('/')) {
    p = '/' + p
  }
  return base + p
}

/**
 * @param {object} opts
 * @param {string} opts.backendBaseUrl
 * @param {string} opts.devApiPrefix
 * @param {object} payload from preload
 */
export async function executeGovernanceHttp(opts, payload) {
  const url = resolveGovernanceUrl(opts.backendBaseUrl, payload.devApiPrefix || opts.devApiPrefix, payload.url || '')
  const method = (payload.method || 'get').toLowerCase()
  const headers = {...(payload.headers || {})}
  const timeout = payload.timeout != null ? Number(payload.timeout) : 10000
  const responseType = payload.responseType === 'blob' || payload.responseType === 'arraybuffer' ? 'arraybuffer' : 'json'

  try {
    const res = await axios({
      method,
      url,
      data: payload.data,
      params: payload.params,
      headers,
      timeout,
      responseType,
      validateStatus: () => true
    })

    const binary = responseType === 'arraybuffer'
    let outData = res.data
    if (binary) {
      const buf = Buffer.isBuffer(res.data) ? res.data : Buffer.from(res.data)
      outData = new Uint8Array(buf)
    }
    return {
      status: res.status,
      statusText: res.statusText,
      headers: flattenHeaders(res.headers),
      data: outData,
      __binary: binary,
      contentType: binary ? String(res.headers['content-type'] || '') : undefined
    }
  } catch (e) {
    const msg = e && e.message ? String(e.message) : String(e)
    return {
      status: 0,
      statusText: 'Error',
      headers: {},
      data: null,
      error: msg,
      __binary: false
    }
  }
}

function flattenHeaders(h) {
  const o = {}
  if (!h || typeof h !== 'object') {
    return o
  }
  for (const [k, v] of Object.entries(h)) {
    if (v == null) {
      continue
    }
    o[k] = Array.isArray(v) ? v.join(', ') : String(v)
  }
  return o
}

/**
 * @param {object} payload debugHttpRequest payload from renderer
 */
export async function executeDebugHttp(payload) {
  const method = (payload.method || 'GET').toUpperCase()
  const url = payload.url
  if (!url || typeof url !== 'string') {
    return {error: '缺少 url', errorCode: TransportErrorCode.POLICY, status: null, headers: {}, bodyText: '', bodyEncoding: 'text', bodyBase64: null}
  }

  const headers = pairsToHeaders(payload.headers)
  const timeout = payload.timeoutMs != null ? Number(payload.timeoutMs) : 60000
  const insecure = Boolean(payload.insecureTls)
  const httpsAgent = insecure ? new https.Agent({rejectUnauthorized: false}) : undefined

  const body = buildAxiosBodyFromSpec(payload.bodySpec, headers)

  try {
    const res = await axios({
      method,
      url,
      headers,
      data: body.data,
      timeout,
      maxRedirects: payload.followRedirects === false ? 0 : 5,
      validateStatus: () => true,
      responseType: 'arraybuffer',
      httpsAgent,
      proxy: false
    })

    const buf = Buffer.from(res.data)
    const max = 8 * 1024 * 1024
    const truncated = buf.length > max
    const slice = truncated ? buf.subarray(0, max) : buf
    const responseCt = String(res.headers['content-type'] || '')
    const preview = decodeBodyPreview(slice, responseCt, buf.length)

    return {
      status: res.status,
      statusText: res.statusText,
      headers: flattenHeaders(res.headers),
      bodyText: truncated ? `${preview.bodyText}\n\n… 响应体已截断（>${max} 字节）` : preview.bodyText,
      bodyEncoding: preview.bodyEncoding,
      bodyBase64: preview.bodyBase64,
      error: null,
      errorCode: null
    }
  } catch (e) {
    const msg = e && e.message ? String(e.message) : String(e)
    const code = classifyNodeNetError(msg)
    return {
      status: null,
      statusText: '',
      headers: {},
      bodyText: '',
      bodyEncoding: 'text',
      bodyBase64: null,
      error: msg,
      errorCode: code
    }
  }
}

/**
 * @returns {{ bodyText: string, bodyEncoding: string, bodyBase64: string|null }}
 */
function decodeBodyPreview(buf, contentType, originalLength) {
  const mime = mediaMimeFromContentType(contentType)
  if (mime) {
    return {
      bodyText: `[binary ${mime} · ${originalLength} bytes]`,
      bodyEncoding: 'base64',
      bodyBase64: buf.toString('base64')
    }
  }
  return {
    bodyText: buf.toString('utf8'),
    bodyEncoding: 'text',
    bodyBase64: null
  }
}

function pairsToHeaders(pairs) {
  const h = {}
  if (!Array.isArray(pairs)) {
    return h
  }
  for (const p of pairs) {
    if (p && p.name) {
      h[p.name] = p.value != null ? String(p.value) : ''
    }
  }
  return h
}

/**
 * @returns {{ data: unknown }}
 */
function buildAxiosBodyFromSpec(spec, headers) {
  if (!spec || spec.kind === 'none') {
    return {data: undefined}
  }
  if (spec.kind === 'raw') {
    return {data: spec.raw}
  }
  if (spec.kind === 'urlencoded') {
    if (!headers['Content-Type'] && !headers['content-type']) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded'
    }
    return {data: spec.raw}
  }
  if (spec.kind === 'json') {
    return {data: spec.json}
  }
  if (spec.kind === 'binary') {
    const buf = Buffer.from(spec.raw || '', 'base64')
    return {data: buf}
  }
  if (spec.kind === 'formData') {
    delete headers['Content-Type']
    delete headers['content-type']
    const fd = new FormData()
    for (const [k, v] of spec.fields || []) {
      fd.append(k, v)
    }
    for (const f of spec.files || []) {
      const buf = Buffer.from(f.base64, 'base64')
      fd.append(f.name, buf, {filename: f.fileName, contentType: f.contentType})
    }
    const formHeaders = fd.getHeaders()
    Object.assign(headers, formHeaders)
    return {data: fd}
  }
  return {data: undefined}
}
