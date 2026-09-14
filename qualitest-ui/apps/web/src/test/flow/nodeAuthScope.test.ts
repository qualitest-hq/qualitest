/**
 * 测 nodeAuthScope：按 pathPrefix 解析凭证变量、登录抽错名、托管头串端。
 * 边界：纯函数，无 UI；凭证目标来自 headerValueTemplate 占位符。
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

const dualAuth = {
  authProfiles: [
    {
      id: 'adminBearer',
      name: '管理端 Bearer',
      match: { pathPrefix: ['/system/', '/monitor/'] },
      headerName: 'Authorization',
      headerValueTemplate: 'Bearer {{asset.adminAuth.token}}',
      credentialApi: { method: 'POST', path: '/login' },
      apis: [
        { apiPath: '/login', authConfig: { mode: 'none' }, requestConfig: { method: 'POST' } },
        { apiPath: '/captchaImage', authConfig: { mode: 'none' }, requestConfig: { method: 'GET' } },
      ],
    },
    {
      id: 'clientBearer',
      name: '客户端 Bearer',
      match: { pathPrefix: ['/api/'] },
      headerName: 'Authorization',
      headerValueTemplate: 'Bearer {{asset.clientAuth.token}}',
      credentialApi: { method: 'POST', path: '/api/account/auth/login' },
      apis: [
        {
          apiPath: '/api/account/auth/login',
          authConfig: { mode: 'none' },
          requestConfig: { method: 'POST' },
        },
      ],
    },
  ],
}

describe('resolveNodeAuthScope', () => {
  it('客户端路径命中 asset.clientAuth.token', () => {
    // 前提：双端 Profile；接口 /api/orders
    const scope = resolveNodeAuthScope({ apiPath: '/api/orders', authConfig: dualAuth })

    // 期望：inherit，凭证 asset.clientAuth.token
    expect(scope.kind).toBe('inherit')
    expect(scope.expectedCredential).toBe('asset.clientAuth.token')
    expect(formatNodeAuthScopeLabel(scope)).toBe('凭证 asset.clientAuth.token')
    expect(scope.conflict).toBe(false)
  })

  it('管理端路径命中 asset.adminAuth.token', () => {
    // 前提：双端 Profile；接口 /system/user/list
    const scope = resolveNodeAuthScope({ apiPath: '/system/user/list', authConfig: dualAuth })

    // 期望：inherit，凭证 asset.adminAuth.token
    expect(scope.kind).toBe('inherit')
    expect(scope.expectedCredential).toBe('asset.adminAuth.token')
    expect(formatNodeAuthScopeLabel(scope)).toBe('凭证 asset.adminAuth.token')
  })

  it('未命中 pathPrefix 时回落到数组第一条', () => {
    // 前提：管理端在前；路径 /other/ping
    const scope = resolveNodeAuthScope({ apiPath: '/other/ping', authConfig: dualAuth })

    // 期望：用管理端 asset.adminAuth.token
    expect(scope.expectedCredential).toBe('asset.adminAuth.token')
    expect(scope.profileName).toContain('管理端')
  })

  it('登录口显示产出变量；抽错名视为冲突', () => {
    // 前提：/login 抽出 flow.token 而非 asset.adminAuth.token
    const scope = resolveNodeAuthScope({
      apiPath: '/login',
      extracts: [{ name: 'token', scope: 'flow', expr: '$.token' }],
      authConfig: dualAuth,
    })

    // 期望：login + 冲突
    expect(scope.kind).toBe('login')
    expect(scope.expectedCredential).toBe('asset.adminAuth.token')
    expect(scope.conflict).toBe(true)
    expect(formatNodeAuthScopeLabel(scope)).toBe('产出 asset.adminAuth.token')
  })

  it('登录口正确写出 asset extract 时无冲突', () => {
    // 前提：/login 抽出 asset.adminAuth.token
    const scope = resolveNodeAuthScope({
      apiPath: '/login',
      extracts: [{
        scope: 'asset',
        entryKey: 'adminAuth',
        fieldPath: 'token',
        expr: '$.token',
        from: 'body',
      }],
      authConfig: dualAuth,
    })

    // 期望：login 且无冲突
    expect(scope.kind).toBe('login')
    expect(scope.conflict).toBe(false)
    expect(scope.extractCredentials).toContain('asset.adminAuth.token')
  })

  it('托管头变量与期望不一致时冲突', () => {
    // 前提：/system 节点托管头写成 asset.clientAuth.token
    const scope = resolveNodeAuthScope({
      apiPath: '/system/user/list',
      headers: [
        {
          name: 'Authorization',
          value: 'Bearer {{asset.clientAuth.token}}',
          profileManaged: true,
        },
      ],
      authConfig: dualAuth,
    })

    // 期望：conflictReason 指出串端
    expect(scope.kind).toBe('inherit')
    expect(scope.headerCredential).toBe('asset.clientAuth.token')
    expect(scope.conflict).toBe(true)
    expect(scope.conflictReason).toContain('adminAuth')
  })

  it('验证码预制口为免登', () => {
    // 前提：GET /captchaImage 在管理端 apis 且 mode=none
    const scope = resolveNodeAuthScope({ apiPath: '/captchaImage', authConfig: dualAuth })

    // 期望：none
    expect(scope.kind).toBe('none')
    expect(formatNodeAuthScopeLabel(scope)).toBe('免登')
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
