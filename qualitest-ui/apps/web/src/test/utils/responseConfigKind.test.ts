/**
 * 测 responseConfig 期望响应形态：xml 规范化、按条目推断、实际形态判定与对照。
 * 边界：jsdom（XML 判定依赖 DOMParser）；不涉及组件渲染。
 * 单跑：pnpm test responseConfigKind
 */
import { describe, expect, it } from 'vitest'

import {
  detectActualResponseKind,
  matchesExpectedResponseKind,
  normalizeExpectedResponseKind,
  parseResponseConfigInput,
} from '@/views/project/testProject/utils/responseConfig'

describe('期望响应形态 xml', () => {
  it('normalize 接受 xml，未知值仍回落 json', () => {
    // 前提：大小写不一的 xml 与未知值
    // 期望：xml 规范为小写；未知值为 json
    expect(normalizeExpectedResponseKind(' XML ')).toBe('xml')
    expect(normalizeExpectedResponseKind('yaml')).toBe('json')
  })

  it('未写期望时按条目推断：全 xml → xml，混合 → any', () => {
    // 前提：responseConfig 无 expectedResponseKind，条目 contentType 全 xml / xml+json 混合
    // 期望：分别推断为 xml 与 any
    const allXml = parseResponseConfigInput(
      JSON.stringify({ configVersion: 1, responses: [{ contentType: 'xml' }, { contentType: 'xml' }] }),
    )
    expect(allXml.bundle.expectedResponseKind).toBe('xml')
    const mixed = parseResponseConfigInput(
      JSON.stringify({ configVersion: 1, responses: [{ contentType: 'xml' }, { contentType: 'json' }] }),
    )
    expect(mixed.bundle.expectedResponseKind).toBe('any')
  })
})

describe('detectActualResponseKind', () => {
  it('text/plain 的 <xml> 报文判为 xml', () => {
    // 前提：微信 v2 风格报文，Content-Type=text/plain
    // 期望：xml
    const xml = '<xml><return_code><![CDATA[SUCCESS]]></return_code></xml>'
    expect(detectActualResponseKind(xml, { 'Content-Type': 'text/plain' })).toBe('xml')
  })

  it('Content-Type 为 application/xml 时按 XML 判定，非法则 nonJson', () => {
    // 前提：声明 application/xml 的合法与非法正文
    // 期望：合法 → xml；非法 → nonJson
    const headers = { 'content-type': 'application/xml; charset=UTF-8' }
    expect(detectActualResponseKind('<result><code>0</code></result>', headers)).toBe('xml')
    expect(detectActualResponseKind('<result><code>0</result>', headers)).toBe('nonJson')
  })

  it('HTML 与 JSON 判定不变', () => {
    // 前提：text/html 页面与 JSON 正文
    // 期望：HTML → nonJson；JSON → json
    expect(detectActualResponseKind('<html><body>x</body></html>', { 'Content-Type': 'text/html' })).toBe('nonJson')
    expect(detectActualResponseKind('{"code":0}')).toBe('json')
  })
})

describe('matchesExpectedResponseKind', () => {
  it('期望 xml 只匹配 XML，期望 json 不匹配 XML', () => {
    // 前提：同一份 XML 报文
    // 期望：xml / any 匹配；json / html 不匹配
    const xml = '<?xml version="1.0"?><r><a>1</a></r>'
    expect(matchesExpectedResponseKind('xml', xml)).toBe(true)
    expect(matchesExpectedResponseKind('any', xml)).toBe(true)
    expect(matchesExpectedResponseKind('json', xml)).toBe(false)
    expect(matchesExpectedResponseKind('html', xml)).toBe(false)
  })
})
