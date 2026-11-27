/**
 * 测画布节点鉴权展示计算。
 * 覆盖：命中 Profile 出「头 …」、mode=none 出「免登」、
 * 本节点鉴权 error 标 conflict、warning/他节点/HTTP 必填不标 conflict、空作用域。
 * 单跑：pnpm test nodeAuthScope
 */
import { describe, expect, it } from 'vitest'

import {
  isProfileManagedRow,
  parseCredentialDisplayPath,
  parsePrimaryCredentialPlaceholder,
} from '@/views/project/testFlow/utils/authHeaderRow'
import {
  resolveNodeAuthScope,
  resolveProfileCredentialFromProjectAuth,
} from '@/views/project/testFlow/utils/nodeAuthScope'

const PROJECT_AUTH = JSON.stringify({
  authProfiles: [
    {
      id: 'clientBearer',
      name: '客户端 Bearer',
      match: { pathPrefix: ['/api/'] },
      headerName: 'Authorization',
      headerValueTemplate: 'Bearer {{asset.clientAuth.token}}',
      apis: [],
    },
  ],
})

describe('resolveNodeAuthScope', () => {
  it('apiAuthMode=inherit 时用 Profile 模板占位', () => {
    const scope = resolveNodeAuthScope({
      nodeId: 'n1',
      apiAuthMode: 'inherit',
      apiPath: '/api/cart/list',
      projectAuthConfig: PROJECT_AUTH,
    })
    expect(scope.label).toBe('头 asset.clientAuth.token')
    expect(scope.expectedCredential).toBe('asset.clientAuth.token')
    expect(scope.conflict).toBe(false)
  })

  it('apiAuthMode=none 时为免登', () => {
    const scope = resolveNodeAuthScope({
      nodeId: 'n1',
      apiAuthMode: 'none',
      apiPath: '/login',
      projectAuthConfig: PROJECT_AUTH,
    })
    expect(scope.label).toBe('免登')
  })

  it('本节点 AUTH issue 时 conflict', () => {
    const scope = resolveNodeAuthScope({
      nodeId: 'n1',
      apiAuthMode: 'inherit',
      apiPath: '/api/x',
      projectAuthConfig: PROJECT_AUTH,
      authIssues: [{
        level: 'error',
        code: 'RUN_READINESS_AUTH',
        message: 'AUTH_LOGIN_FLOWKEY_COLLISION: 登录键碰撞',
        nodeIds: ['n1'],
      }],
    })
    expect(scope.conflict).toBe(true)
    expect(scope.conflictReason).toContain('登录键碰撞')
    expect(scope.conflictReason).not.toMatch(/^AUTH_LOGIN_FLOWKEY_COLLISION/)
  })

  it('本节点 AUTH WARNING 不标 conflict', () => {
    const scope = resolveNodeAuthScope({
      nodeId: 'n1',
      apiAuthMode: 'inherit',
      apiPath: '/api/x',
      projectAuthConfig: PROJECT_AUTH,
      authIssues: [{
        level: 'warning',
        code: 'RUN_READINESS_AUTH',
        message: 'AUTH_LOGIN_FLOWKEY_COLLISION: 登录键碰撞',
        nodeIds: ['n1'],
      }],
    })
    expect(scope.conflict).toBe(false)
  })

  it('其它节点的 AUTH issue 不标本节点 conflict', () => {
    const scope = resolveNodeAuthScope({
      nodeId: 'n1',
      apiAuthMode: 'inherit',
      apiPath: '/api/x',
      projectAuthConfig: PROJECT_AUTH,
      authIssues: [{
        level: 'error',
        code: 'RUN_READINESS_AUTH',
        message: 'AUTH_LOGIN_FLOWKEY_COLLISION: 碰撞',
        nodeIds: ['n2'],
      }],
    })
    expect(scope.conflict).toBe(false)
  })

  it('HTTP 必填码不标鉴权 conflict', () => {
    const scope = resolveNodeAuthScope({
      nodeId: 'n1',
      apiAuthMode: 'inherit',
      apiPath: '/api/x',
      projectAuthConfig: PROJECT_AUTH,
      authIssues: [{
        level: 'error',
        code: 'RUN_READINESS_HTTP_PARAM',
        message: '缺必填测值',
        nodeIds: ['n1'],
      }],
    })
    expect(scope.conflict).toBe(false)
  })

  it('无 mode 无 Profile 命中时为空作用域', () => {
    const scope = resolveNodeAuthScope({ nodeId: 'n1' })
    expect(scope.label).toBe('')
  })
})

describe('resolveProfileCredentialFromProjectAuth', () => {
  it('最长 pathPrefix 命中客户端模板', () => {
    const hit = resolveProfileCredentialFromProjectAuth(PROJECT_AUTH, '/api/cart')
    expect(hit.credentialPath).toBe('asset.clientAuth.token')
    expect(hit.profileName).toBe('客户端 Bearer')
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
