const MAX_FORM_FILE_BYTES = 2 * 1024 * 1024

function uint8ToBase64(bytes) {
  const len = bytes.byteLength
  let binary = ''
  const step = 0x8000
  for (let i = 0; i < len; i += step) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, Math.min(i + step, len)))
  }
  return btoa(binary)
}

/**
 * 将调试请求体归一化为与 Electron / Java 对齐的 bodySpec
 * @param {string} method
 * @param {unknown} data
 * @returns {Promise<{ kind: string, raw?: string, json?: object, fields?: string[][], files?: object[] }>}
 */
export async function normalizeDebugBodySpec(method, data) {
  const m = String(method || 'GET').toUpperCase()
  if (['GET', 'HEAD'].includes(m)) {
    return {kind: 'none'}
  }
  if (data == null || data === '') {
    return {kind: 'none'}
  }
  if (typeof data === 'string') {
    return {kind: 'raw', raw: data}
  }
  if (typeof URLSearchParams !== 'undefined' && data instanceof URLSearchParams) {
    return {kind: 'urlencoded', raw: data.toString()}
  }
  if (typeof File !== 'undefined' && data instanceof File) {
    if (data.size > MAX_FORM_FILE_BYTES) {
      throw new Error(`调试暂不支持超过 ${MAX_FORM_FILE_BYTES / 1024 / 1024}MB 的二进制文件`)
    }
    const buf = await data.arrayBuffer()
    const bytes = new Uint8Array(buf)
    return {
      kind: 'binary',
      raw: uint8ToBase64(bytes),
      contentType: data.type && data.type.trim() ? data.type : 'application/octet-stream',
      fileName: data.name || 'file'
    }
  }
  if (typeof Blob !== 'undefined' && data instanceof Blob && !(data instanceof File)) {
    if (data.size > MAX_FORM_FILE_BYTES) {
      throw new Error(`调试暂不支持超过 ${MAX_FORM_FILE_BYTES / 1024 / 1024}MB 的请求体`)
    }
    const buf = await data.arrayBuffer()
    const bytes = new Uint8Array(buf)
    return {
      kind: 'binary',
      raw: uint8ToBase64(bytes),
      contentType: data.type && data.type.trim() ? data.type : 'application/octet-stream',
      fileName: 'blob'
    }
  }
  if (typeof FormData !== 'undefined' && data instanceof FormData) {
    const fields = []
    const files = []
    for (const [key, val] of data.entries()) {
      if (typeof File !== 'undefined' && val instanceof File) {
        if (val.size > MAX_FORM_FILE_BYTES) {
          throw new Error(`调试暂不支持超过 ${MAX_FORM_FILE_BYTES / 1024 / 1024}MB 的文件字段：${key}`)
        }
        const buf = await val.arrayBuffer()
        const bytes = new Uint8Array(buf)
        files.push({
          name: key,
          fileName: val.name,
          contentType: val.type && val.type.trim() ? val.type : 'application/octet-stream',
          base64: uint8ToBase64(bytes)
        })
      } else {
        fields.push([key, String(val)])
      }
    }
    return {kind: 'formData', fields, files}
  }
  if (typeof data === 'object') {
    return {kind: 'json', json: data}
  }
  return {kind: 'raw', raw: String(data)}
}
