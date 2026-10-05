/**
 * 测调试台托管鉴权头：行生成、手动行覆盖、取消勾选、占位符未解析提示。
 * 边界：纯函数，不发请求。
 * 单跑：pnpm test debugManagedAuthRow   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import { buildDebugFlowContext } from '@/utils/flow/flowContextBuilder'
import {
  buildManagedRow,
  isManagedRowOverridden,
  pickHeadersForSend
} from '@/views/project/testProject/utils/debugManagedAuthRow'
import {
  collectUnresolvedPlaceholders,
  resolvePlaceholderInString
} from '@/views/project/testProject/utils/debugPlaceholderResolve'
import { rowsToKeyValueObject } from '@/views/project/testProject/composables/useApiDebugPersist'

describe('调试台托管鉴权头', () => {
  const header = {
    name: 'Authorization',
    valueTemplate: 'Bearer {{asset.clientAuth.token}}',
    profileName: '客户端 Bearer',
    source: 'profile'
  }

  it('生成只读托管行且默认勾选', () => {
    // 前提：详情带回客户端托管头
    // 期望：第一行标记为托管，名称和模板就位
    const row = buildManagedRow(header, true)
    expect(row).not.toBeNull()
    expect(row!._managed).toBe(true)
    expect(row!._enabled).toBe(true)
    expect(row!.name).toBe('Authorization')
    expect(row!.value).toBe('Bearer {{asset.clientAuth.token}}')
    expect(row!.profileName).toBe('客户端 Bearer')
  })

  it('没有托管头时不生成行', () => {
    // 前提：免登录接口，详情没有 managedAuthHeader
    // 期望：返回 null
    expect(buildManagedRow(null, true)).toBeNull()
  })

  it('取消勾选后不发送托管头', () => {
    // 前提：托管行未勾选，没有手动头
    // 期望：发送头为空
    const row = buildManagedRow(header, false)
    expect(pickHeadersForSend([row, { _enabled: true, name: '', value: '' }])).toEqual({})
  })

  it('同名手动行优先，托管行不发送', () => {
    // 前提：托管行勾选，另有已启用的 Authorization
    // 期望：只发手动值，并判定托管行被覆盖
    const managed = buildManagedRow(header, true)
    const manual = { _enabled: true, name: 'Authorization', value: 'Bearer manual' }
    const rows = [managed, manual]
    expect(pickHeadersForSend(rows)).toEqual({ Authorization: 'Bearer manual' })
    expect(isManagedRowOverridden(rows, managed)).toBe(true)
  })

  it('保存时排除托管行', () => {
    // 前提：表里有托管行和一行自定义头
    // 期望：入库对象只有自定义头
    const managed = buildManagedRow(header, true)
    const custom = { _enabled: true, name: 'X-Trace', value: '1' }
    expect(rowsToKeyValueObject([managed, custom])).toEqual({ 'X-Trace': '1' })
  })

  it('素材有值时替换占位符，空值记为未解析', () => {
    // 前提：素材 clientAuth.token 有值，adminAuth.token 为空
    // 期望：客户端模板换成实际值；管理端路径进入未解析列表
    const ctx = buildDebugFlowContext({
      assetEntries: [
        { key: 'clientAuth', assets: { clientAuth: { token: 'tok-1' } } },
        { key: 'adminAuth', assets: { adminAuth: { token: '' } } }
      ]
    })
    expect(resolvePlaceholderInString('Bearer {{asset.clientAuth.token}}', ctx)).toBe('Bearer tok-1')
    expect(collectUnresolvedPlaceholders(
        ['Bearer {{asset.clientAuth.token}}', 'Bearer {{asset.adminAuth.token}}'],
        ctx
    )).toEqual(['asset.adminAuth.token'])
  })
})
