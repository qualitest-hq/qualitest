/**
 * mustacheScan 单元测试：合法路径占位、假 {{ 混排；反斜杠原样。
 * 边界：空 {{}}、未闭合、简写标识符不参与默认求值。
 * 单跑：pnpm test mustacheScan（在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import {
  forEachMustache,
  isMustachePlaceholderPath,
  isShortMustacheIdentifier,
  listMustacheInners,
  listMustacheInnersWhere,
  replaceMustache,
} from '@/utils/flow/mustacheScan'

describe('mustacheScan', () => {
  it('listMustacheInners 保序收集合法路径', () => {
    expect(listMustacheInners('Bearer {{flow.a}} / {{ asset.x.y }}')).toEqual([
      'flow.a',
      'asset.x.y',
    ])
  })

  it('空 {{}} 跳过；replace 替换合法路径', () => {
    expect(replaceMustache('{{flow.t}}', () => 'X')).toBe('X')
    expect(replaceMustache('{{}}{{flow.t}}', () => 'ok')).toBe('{{}}ok')
    expect(listMustacheInners('{{}}{{flow.t}}')).toEqual(['flow.t'])
  })

  it('未闭合定界符保留原文', () => {
    let n = 0
    forEachMustache('pre {{flow.x', () => {
      n++
    })
    expect(n).toBe(0)
    expect(replaceMustache('pre {{flow.x', () => 'Y')).toBe('pre {{flow.x')
  })

  it('字面 {{ 混排时仍命中真占位', () => {
    const raw = '请写 {{ 再填 {{flow.x}} 结束'
    expect(listMustacheInners(raw)).toEqual(['flow.x'])
    expect(replaceMustache(raw, () => 'OK')).toBe('请写 {{ 再填 OK 结束')
    expect(isMustachePlaceholderPath('flow.token')).toBe(true)
    expect(isMustachePlaceholderPath('token')).toBe(false)
  })

  it('简写标识符仅在 where 谓词下收集', () => {
    expect(isShortMustacheIdentifier('token')).toBe(true)
    expect(listMustacheInners('{{token}}')).toEqual([])
    expect(listMustacheInnersWhere('{{token}}', isShortMustacheIdentifier)).toEqual(['token'])
  })

  it('replace 保留 $；自带反斜杠不剥除', () => {
    expect(replaceMustache('price {{flow.p}}', () => '$1.00')).toBe('price $1.00')
    // 前提：占位前有对方业务反斜杠
    // 期望：\ 留下，{{flow.x}} 仍按路径求值
    expect(replaceMustache('show \\{{flow.x}} end', () => 'NO')).toBe('show \\NO end')
  })
})
