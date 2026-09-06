/**
 * 从 HTTP 调试结果或测试流 Run 步骤响应中识别可预览媒体。
 *
 * 识别顺序：
 * 1. bodyBase64（裸二进制，配合 Content-Type / bodyEncoding）
 * 2. 纯字符串：data: URL 或 http(s) 媒体链接
 * 3. JSON 对象常见字段（img / videoUrl 等）中的 base64、data: 或 URL
 *
 * 无法识别时返回 null，调用方不渲染预览区。
 */

import { mediaMimeFromContentType } from '@qualitest/transport-types'

/** 预览种类 */
export type ResponseMediaKind = 'image' | 'video' | 'audio' | 'pdf' | 'link'

/** 识别输入：调试结果或 Run 步骤响应片段 */
export interface ResponseMediaPreviewInput {
  /** 已解析的响应体对象（优先于 bodyText） */
  body?: unknown
  /** 响应体文本（JSON 字符串或纯文本） */
  bodyText?: string | null
  /** 响应头，用于读取 Content-Type */
  headers?: Record<string, string> | null
  /** 裸媒体响应的 Base64 字节 */
  bodyBase64?: string | null
  /** text | base64；base64 表示整包响应为媒体二进制 */
  bodyEncoding?: string | null
  /** 响应是否因体积上限被截断 */
  truncated?: boolean
}

/** 识别结果：交给 UI 按 kind 渲染 */
export interface ResponseMediaPreview {
  kind: ResponseMediaKind
  /** img/video/audio/iframe 的 src，或外链 href */
  src: string
  mime: string
  truncated?: boolean
}

export { mediaMimeFromContentType }

/** 判断 Content-Type 是否为可预览媒体 */
export function isMediaContentType(contentType: string | null | undefined): boolean {
  return mediaMimeFromContentType(contentType) != null
}

/**
 * JSON 内嵌媒体字段规则：按字段名猜测默认 MIME。
 * img 等默认 image/gif（常见验证码图编码）；video 默认 video/mp4。
 */
const FIELD_RULES: ReadonlyArray<{ keys: readonly string[]; kind: ResponseMediaKind | null; mime: string }> = [
  { keys: ['img', 'image', 'captcha', 'avatar'], kind: 'image', mime: 'image/gif' },
  { keys: ['video', 'videoUrl'], kind: 'video', mime: 'video/mp4' },
  { keys: ['audio', 'audioUrl'], kind: 'audio', mime: 'audio/mpeg' },
  { keys: ['pdf', 'fileUrl'], kind: 'pdf', mime: 'application/pdf' },
  { keys: ['url'], kind: null, mime: '' }
]

const DATA_URL_RE = /^data:([a-zA-Z0-9][a-zA-Z0-9!#$&\-^_.+/]*)?(;[^,]*)?;base64,([A-Za-z0-9+/=\s]+)$/i
const HTTP_URL_RE = /^https?:\/\//i
const BASE64_RE = /^[A-Za-z0-9+/]+={0,2}$/

/**
 * 解析响应，返回预览描述或 null。
 */
export function resolveResponseMediaPreview(
  input: ResponseMediaPreviewInput | null | undefined
): ResponseMediaPreview | null {
  if (!input) {
    return null
  }
  const truncated = !!input.truncated
  const mimeFromHeaders = contentTypeFromHeaders(input.headers)

  // 裸媒体：整包响应为二进制，已编成 Base64
  if (input.bodyBase64) {
    const mime =
      mimeFromHeaders ||
      (input.bodyEncoding === 'base64' ? 'application/octet-stream' : '')
    if (mime) {
      const kind = kindFromMime(mime)
      if (kind && kind !== 'link') {
        return {
          kind,
          src: toDataUrl(mime, input.bodyBase64),
          mime,
          truncated: truncated || undefined
        }
      }
    }
  }

  const parsed = coerceBody(input.body, input.bodyText)
  if (parsed == null) {
    return null
  }

  if (typeof parsed === 'string') {
    return previewFromString(parsed, truncated)
  }

  if (typeof parsed === 'object' && !Array.isArray(parsed)) {
    const obj = parsed as Record<string, unknown>
    for (const rule of FIELD_RULES) {
      for (const key of rule.keys) {
        const hit = previewFromField(obj[key], rule.kind, rule.mime, truncated)
        if (hit) return hit
      }
    }
  }

  return null
}

/** 优先用已解析 body，否则尝试解析 bodyText */
function coerceBody(body: unknown, bodyText: string | null | undefined): unknown {
  if (body != null && typeof body === 'object') {
    return body
  }
  if (typeof body === 'string') {
    return tryParseJsonOrString(body)
  }
  if (typeof bodyText === 'string' && bodyText.length > 0) {
    return tryParseJsonOrString(bodyText)
  }
  return null
}

/** 像 JSON 则 parse，否则返回去空白后的字符串 */
function tryParseJsonOrString(raw: string): unknown {
  const trimmed = raw.trim()
  if (!trimmed) {
    return null
  }
  if (
    (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
    (trimmed.startsWith('[') && trimmed.endsWith(']'))
  ) {
    try {
      return JSON.parse(trimmed)
    } catch {
      /* fall through */
    }
  }
  return trimmed
}

/** 整段 body 为 data: 或 http(s) 时的预览 */
function previewFromString(value: string, truncated: boolean): ResponseMediaPreview | null {
  const data = matchDataUrl(value)
  if (data) {
    return {...data, truncated: truncated || undefined}
  }
  if (HTTP_URL_RE.test(value)) {
    return previewFromHttpUrl(value, truncated)
  }
  return null
}

/**
 * 处理 JSON 某一字段：data: URL、http(s) URL，或纯 base64（需 forcedKind）。
 */
function previewFromField(
  value: unknown,
  forcedKind: ResponseMediaKind | null,
  defaultMime: string,
  truncated: boolean
): ResponseMediaPreview | null {
  if (value == null) {
    return null
  }
  const str = String(value).trim()
  if (!str) {
    return null
  }

  const data = matchDataUrl(str)
  if (data) {
    return {...data, truncated: truncated || undefined}
  }

  if (HTTP_URL_RE.test(str)) {
    const fromUrl = previewFromHttpUrl(str, truncated)
    if (fromUrl) {
      if (forcedKind && fromUrl.kind === 'link') {
        return {kind: forcedKind, src: str, mime: defaultMime || fromUrl.mime, truncated: truncated || undefined}
      }
      return fromUrl
    }
  }

  if (looksLikeBase64(str) && forcedKind && forcedKind !== 'link') {
    const mime = defaultMime || mimeForKind(forcedKind)
    return {
      kind: forcedKind,
      src: toDataUrl(mime, str.replace(/\s+/g, '')),
      mime,
      truncated: truncated || undefined
    }
  }

  return null
}

/** 匹配 data:mime;base64,... 形式的内嵌资源 */
function matchDataUrl(value: string): ResponseMediaPreview | null {
  const m = DATA_URL_RE.exec(value.trim())
  if (!m) {
    return null
  }
  const mime = (m[1] || 'application/octet-stream').toLowerCase()
  const kind = kindFromMime(mime)
  if (!kind || kind === 'link') {
    return null
  }
  return {kind, src: value.trim(), mime}
}

/** http(s) URL：按路径扩展名猜种类，猜不出则为外链 */
function previewFromHttpUrl(url: string, truncated: boolean): ResponseMediaPreview | null {
  const kind = kindFromUrl(url)
  const mime = mimeForKind(kind)
  return {
    kind,
    src: url,
    mime,
    truncated: truncated || undefined
  }
}

function kindFromUrl(url: string): ResponseMediaKind {
  const path = url.split('?')[0].split('#')[0].toLowerCase()
  if (/\.(png|jpe?g|gif|webp|bmp|svg|ico)$/.test(path)) return 'image'
  if (/\.(mp4|webm|ogg|mov|m4v)$/.test(path)) return 'video'
  if (/\.(mp3|wav|ogg|aac|m4a|flac)$/.test(path)) return 'audio'
  if (/\.pdf$/.test(path)) return 'pdf'
  return 'link'
}

function kindFromMime(mime: string): ResponseMediaKind | null {
  const m = (mime || '').toLowerCase().split(';')[0].trim()
  if (m.startsWith('image/')) return 'image'
  if (m.startsWith('video/')) return 'video'
  if (m.startsWith('audio/')) return 'audio'
  if (m === 'application/pdf') return 'pdf'
  return null
}

/** 种类缺省 MIME（纯 base64 字段无 CT 时使用） */
function mimeForKind(kind: ResponseMediaKind): string {
  switch (kind) {
    case 'image':
      return 'image/gif'
    case 'video':
      return 'video/mp4'
    case 'audio':
      return 'audio/mpeg'
    case 'pdf':
      return 'application/pdf'
    default:
      return 'application/octet-stream'
  }
}

function contentTypeFromHeaders(headers: Record<string, string> | null | undefined): string {
  if (!headers || typeof headers !== 'object') {
    return ''
  }
  for (const [k, v] of Object.entries(headers)) {
    if (k && k.toLowerCase() === 'content-type' && v) {
      return String(v).split(';')[0].trim().toLowerCase()
    }
  }
  return ''
}

/** 粗检：长度合法且字符集像 Base64 */
function looksLikeBase64(value: string): boolean {
  const compact = value.replace(/\s+/g, '')
  if (compact.length < 16 || compact.length % 4 !== 0) {
    return false
  }
  return BASE64_RE.test(compact)
}

function toDataUrl(mime: string, base64: string): string {
  const clean = String(base64).replace(/\s+/g, '')
  return `data:${mime};base64,${clean}`
}
