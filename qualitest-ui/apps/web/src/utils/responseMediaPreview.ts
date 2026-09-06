/**
 * 从 HTTP 调试 / Run 步骤响应中识别可预览媒体（图 / 音视频 / PDF / 外链）。
 */

import { mediaMimeFromContentType } from '@qualitest/transport-types'

export type ResponseMediaKind = 'image' | 'video' | 'audio' | 'pdf' | 'link'

export interface ResponseMediaPreviewInput {
  body?: unknown
  bodyText?: string | null
  headers?: Record<string, string> | null
  bodyBase64?: string | null
  bodyEncoding?: string | null
  truncated?: boolean
}

export interface ResponseMediaPreview {
  kind: ResponseMediaKind
  src: string
  mime: string
  truncated?: boolean
}

export { mediaMimeFromContentType }

/** 是否为可预览媒体 Content-Type */
export function isMediaContentType(contentType: string | null | undefined): boolean {
  return mediaMimeFromContentType(contentType) != null
}

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

export function resolveResponseMediaPreview(
  input: ResponseMediaPreviewInput | null | undefined
): ResponseMediaPreview | null {
  if (!input) {
    return null
  }
  const truncated = !!input.truncated
  const mimeFromHeaders = contentTypeFromHeaders(input.headers)

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
