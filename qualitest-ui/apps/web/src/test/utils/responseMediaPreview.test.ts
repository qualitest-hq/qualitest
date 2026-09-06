/**
 * 测 resolveResponseMediaPreview：从 HTTP 响应识别可预览媒体。
 * 边界：纯函数；JSON img / data: / URL / bodyBase64 / 无媒体。
 * 单跑：pnpm test responseMediaPreview
 */
import { describe, expect, it } from 'vitest'

import { resolveResponseMediaPreview } from '@/utils/responseMediaPreview'

describe('resolveResponseMediaPreview', () => {
  it('识别验证码 JSON 的 img 纯 base64 为 image/gif', () => {
    // 前提：body 为 { img: base64, uuid }
    // 期望：kind=image，src 为 data:image/gif;base64,...
    const img =
      'R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7'
    const hit = resolveResponseMediaPreview({
      body: { img, uuid: 'abc-123', captchaEnabled: true }
    })
    expect(hit).not.toBeNull()
    expect(hit!.kind).toBe('image')
    expect(hit!.mime).toBe('image/gif')
    expect(hit!.src).toBe(`data:image/gif;base64,${img}`)
  })

  it('识别 bodyText 中的 data:image URL', () => {
    // 前提：bodyText 为完整 data URL 字符串
    // 期望：kind=image，src 原样保留
    const src = 'data:image/png;base64,iVBORw0KGgo='
    const hit = resolveResponseMediaPreview({ bodyText: src })
    expect(hit).toEqual({ kind: 'image', src, mime: 'image/png' })
  })

  it('识别 bodyEncoding=base64 的裸二进制图', () => {
    // 前提：bodyEncoding + bodyBase64 + Content-Type=image/png
    // 期望：拼出 data:image/png;base64,...
    const b64 = 'iVBORw0KGgo='
    const hit = resolveResponseMediaPreview({
      bodyEncoding: 'base64',
      bodyBase64: b64,
      headers: { 'Content-Type': 'image/png' },
      bodyText: '[binary image/png · 12 bytes]'
    })
    expect(hit!.kind).toBe('image')
    expect(hit!.src).toBe(`data:image/png;base64,${b64}`)
  })

  it('识别 JSON 内 http(s) 视频 URL', () => {
    // 前提：字段 videoUrl 为 mp4 链接
    // 期望：kind=video
    const url = 'https://cdn.example.com/demo/clip.mp4'
    const hit = resolveResponseMediaPreview({
      bodyText: JSON.stringify({ videoUrl: url })
    })
    expect(hit).toEqual({ kind: 'video', src: url, mime: 'video/mp4' })
  })

  it('无法识别媒体时返回 null', () => {
    // 前提：普通 JSON 文本响应
    // 期望：null
    const hit = resolveResponseMediaPreview({
      bodyText: JSON.stringify({ code: 200, msg: 'ok', data: { name: 'x' } })
    })
    expect(hit).toBeNull()
  })

  it('截断标记透传到结果', () => {
    // 前提：truncated=true 且有可预览 img
    // 期望：结果带 truncated
    const img = 'R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7'
    const hit = resolveResponseMediaPreview({
      body: { img },
      truncated: true
    })
    expect(hit!.truncated).toBe(true)
  })
})
