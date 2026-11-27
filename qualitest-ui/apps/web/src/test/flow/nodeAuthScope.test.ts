/**
 * 测 nodeAuthScope：凭证展示读节点 headers/extracts；conflict 只认 AUTH issues。
 * 边界：纯函数，无 pathPrefix 推演。
 * 单跑：pnpm test nodeAuthScope   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import {
  isProfileManagedRow,
  parseCredentialDisplayPath,
  parsePrimaryCredentialPlaceholder,
} from '@/views/project/testFlow/utils/authHeaderRow'
import {
  formatNodeAuthScopeLabel,
  resolveNodeAuthScope,
} from '@/views/project/testFlow/utils/nodeAuthScope'

describe('resolveNodeAuthScope', () => {
  it('托管头展示凭证路径且无 issue 时不冲突', () => {
    // 前提：节点有 profileManaged 托管头；无 AUTH issue
    const scope = resolveNodeAuthScope({
      nodeId: 'n1',
      headers: [
        {
          name: 'Authorization',
          value: 'Bearer {{asset.clientAuth.token}}',
          profileManaged: true,
        },
      ],
    })

    // 期望：inherit，凭证来自托管头，无 conflict
    expect(scope.kind).toBe('inherit')
    expect(scope.expectedCredential).toBe('asset.clientAuth.token')
    expect(formatNodeAuthScopeLabel(scope)).toBe('凭证 asset.clientAuth.token')
    expect(scope.conflict).toBe(false)
  })

  it('extracts 产出凭证时为 login', () => {
    // 前提：节点 extracts 写出 asset.adminAuth.token
    const scope = resolveNodeAuthScope({
      nodeId: 'login',
      extracts: [{
        scope: 'asset',
        entryKey: 'adminAuth',
        fieldPath: 'token',
        expr: '$.token',
        from: 'body',
      }],
    })

    // 期望：login 且标签为产出
    expect(scope.kind).toBe('login')
    expect(scope.extractCredentials).toContain('asset.adminAuth.token')
    expect(formatNodeAuthScopeLabel(scope)).toBe('产出 asset.adminAuth.token')
    expect(scope.conflict).toBe(false)
  })

  it('本节点 AUTH issue 时 conflict', () => {
    // 前提：canvas issue AUTH_TOKEN_MISSING 挂在本节点
    const scope = resolveNodeAuthScope({
      nodeId: 'n1',
      headers: [
        {
          name: 'Authorization',
          value: 'Bearer {{asset.adminAuth.token}}',
          profileManaged: true,
        },
      ],
      authIssues: [{
        level: 'error',
        code: 'RUN_READINESS_AUTH',
        message: 'AUTH_TOKEN_MISSING: 缺少凭证来源 asset.adminAuth.token',
        nodeIds: ['n1'],
      }],
    })

    // 期望：conflictReason 去掉 CODE 前缀
    expect(scope.conflict).toBe(true)
    expect(scope.conflictReason).toContain('缺少凭证来源')
    expect(scope.conflictReason).not.toMatch(/^AUTH_TOKEN_MISSING/)
  })

  it('本节点 AUTH WARNING 不标 conflict', () => {
    // 前提：鉴权问题被 profile 降为 WARNING
    // 期望：不进硬样式 conflict
    const scope = resolveNodeAuthScope({
      nodeId: 'n1',
      headers: [
        {
          name: 'Authorization',
          value: 'Bearer {{asset.adminAuth.token}}',
          profileManaged: true,
        },
      ],
      authIssues: [{
        level: 'warning',
        code: 'RUN_READINESS_AUTH',
        message: 'AUTH_TOKEN_MISSING: 缺少凭证来源 asset.adminAuth.token',
        nodeIds: ['n1'],
      }],
    })
    expect(scope.conflict).toBe(false)
  })

  it('其它节点的 AUTH issue 不标本节点 conflict', () => {
    // 前提：issue 只挂 n2
    const scope = resolveNodeAuthScope({
      nodeId: 'n1',
      headers: [
        {
          name: 'Authorization',
          value: 'Bearer {{asset.adminAuth.token}}',
          profileManaged: true,
        },
      ],
      authIssues: [{
        level: 'error',
        code: 'RUN_READINESS_AUTH',
        message: 'AUTH_TOKEN_MISSING: 缺凭证',
        nodeIds: ['n2'],
      }],
    })

    expect(scope.conflict).toBe(false)
  })

  it('无托管头无 extracts 无 issue 时为空作用域', () => {
    // 前提：空节点
    const scope = resolveNodeAuthScope({ nodeId: 'n1' })
    expect(scope.kind).toBe('empty')
    expect(formatNodeAuthScopeLabel(scope)).toBe('')
  })
})

describe('authHeaderRow', () => {
  it('识别托管标记与 {{asset.*}} / {{flow.*}} 占位', () => {
    expect(isProfileManagedRow({ profileManaged: true })).toBe(true)
    expect(isProfileManagedRow({ profileManaged: 'true' })).toBe(true)
    expect(isProfileManagedRow({ name: 'Authorization' })).toBe(false)
    expect(parseCredentialDisplayPath('Bearer {{asset.adminAuth.token}}')).toBe('asset.adminAuth.token')
    expect(parsePrimaryCredentialPlaceholder('Bearer {{flow.adminToken}}')?.flowKey).toBe('adminToken')
    expect(parsePrimaryCredentialPlaceholder('Bearer {{asset.adminAuth.token}}')?.scope).toBe('asset')
    expect(parsePrimaryCredentialPlaceholder('static')).toBeNull()
  })
})
