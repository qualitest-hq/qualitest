/**
 * 画布 HTTP 节点的鉴权作用域：按项目 authProfiles 解析将使用 / 将产出的 flow 变量。
 * 对齐后端 ProjectAuthConfigSupport.resolveProfileId（最长 pathPrefix，未命中用数组第一条）。
 */

import { parseAuthConfig, splitPathLines } from '@/views/project/testProject/utils/projectAuthConfig'

import { isProfileManagedRow, parseFlowPlaceholderKey } from './authHeaderRow'

export type NodeAuthKind = 'login' | 'none' | 'inherit' | 'empty'

export interface NodeAuthScope {
  kind: NodeAuthKind
  profileName: string
  expectedFlowKey: string
  headerFlowKey: string
  extractFlowKeys: string[]
  conflict: boolean
  conflictReason: string
}

function normalizeApiPath(apiPath: string) {
  let p = String(apiPath || '').trim()
  if (!p) return '/'
  if (!p.startsWith('/')) p = '/' + p
  while (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1)
  return p
}

function normalizePrefix(prefix: string) {
  let p = String(prefix || '').trim()
  if (!p || p === '/') return ''
  if (!p.startsWith('/')) p = '/' + p
  if (!p.endsWith('/')) p += '/'
  return p
}

function pathsEqual(a: string, b: string) {
  return normalizeApiPath(a) === normalizeApiPath(b)
}

function listFlowExtractKeys(extracts: unknown): string[] {
  if (!Array.isArray(extracts)) return []
  const keys: string[] = []
  for (const row of extracts) {
    if (!row || typeof row !== 'object') continue
    const r = row as Record<string, unknown>
    const scope = r.scope != null ? String(r.scope).trim() : ''
    if (scope && scope.toLowerCase() !== 'flow') continue
    const name = r.name != null ? String(r.name).trim() : ''
    if (name && !keys.includes(name)) keys.push(name)
  }
  return keys
}

function parseHeaderFlowKey(headers: unknown): string {
  if (!Array.isArray(headers)) return ''
  for (const row of headers) {
    if (!row || typeof row !== 'object') continue
    const r = row as Record<string, unknown>
    if (!isProfileManagedRow(r)) continue
    const key = parseFlowPlaceholderKey(r.value)
    if (key) return key
  }
  return ''
}

function resolveProfile(apiPath: string, profiles: ReturnType<typeof parseAuthConfig>['profiles']) {
  const path = normalizeApiPath(apiPath)
  let best: (typeof profiles)[0] | null = null
  let bestLen = -1
  for (const p of profiles) {
    const prefixes = splitPathLines(p.pathPrefixText)
    for (const raw of prefixes) {
      const prefix = normalizePrefix(raw)
      if (!prefix) continue
      if ((path + '/').startsWith(prefix) && prefix.length > bestLen) {
        bestLen = prefix.length
        best = p
      }
    }
  }
  return best || profiles[0] || null
}

function findCredentialProfile(
  apiPath: string,
  profiles: ReturnType<typeof parseAuthConfig>['profiles'],
) {
  return profiles.find((p) => p.credentialPath && pathsEqual(p.credentialPath, apiPath)) || null
}

function isAnonymousPrefabricated(
  apiPath: string,
  profiles: ReturnType<typeof parseAuthConfig>['profiles'],
) {
  for (const p of profiles) {
    for (const api of p.apis || []) {
      if (pathsEqual(api.apiPath, apiPath) && String(api.authMode || '').toLowerCase() === 'none') {
        return true
      }
    }
  }
  return false
}

export function emptyNodeAuthScope(): NodeAuthScope {
  return {
    kind: 'empty',
    profileName: '',
    expectedFlowKey: '',
    headerFlowKey: '',
    extractFlowKeys: [],
    conflict: false,
    conflictReason: '',
  }
}

/**
 * 解析节点将使用或产出的凭证变量。
 */
export function resolveNodeAuthScope(input: {
  apiPath?: string
  headers?: unknown
  extracts?: unknown
  authConfig?: unknown
}): NodeAuthScope {
  const form = parseAuthConfig(input.authConfig)
  const profiles = form.profiles || []
  if (!profiles.length) {
    return emptyNodeAuthScope()
  }
  const apiPath = String(input.apiPath || '')
  const extractFlowKeys = listFlowExtractKeys(input.extracts)
  const headerFlowKey = parseHeaderFlowKey(input.headers)
  const credential = findCredentialProfile(apiPath, profiles)
  if (credential) {
    const expectedFlowKey = String(credential.loginFlowKey || '').trim()
    const conflict = !!expectedFlowKey && !extractFlowKeys.includes(expectedFlowKey)
    return {
      kind: 'login',
      profileName: credential.name || credential.id,
      expectedFlowKey,
      headerFlowKey,
      extractFlowKeys,
      conflict,
      conflictReason: conflict
        ? `登录口应产出 flow.${expectedFlowKey}，当前 extracts 未写出该变量`
        : '',
    }
  }
  if (isAnonymousPrefabricated(apiPath, profiles)) {
    return {
      kind: 'none',
      profileName: '',
      expectedFlowKey: '',
      headerFlowKey,
      extractFlowKeys,
      conflict: false,
      conflictReason: '',
    }
  }
  const profile = resolveProfile(apiPath, profiles)
  const expectedFlowKey = String(profile?.loginFlowKey || '').trim()
  const conflict = !!headerFlowKey && !!expectedFlowKey && headerFlowKey !== expectedFlowKey
  return {
    kind: 'inherit',
    profileName: profile?.name || profile?.id || '',
    expectedFlowKey,
    headerFlowKey,
    extractFlowKeys,
    conflict,
    conflictReason: conflict
      ? `托管头使用 flow.${headerFlowKey}，按 pathPrefix 应为 flow.${expectedFlowKey}`
      : '',
  }
}

/** 卡片/属性面板短文案 */
export function formatNodeAuthScopeLabel(scope: NodeAuthScope): string {
  if (scope.kind === 'empty') return ''
  if (scope.kind === 'none') return '免登'
  if (scope.kind === 'login') {
    return scope.expectedFlowKey ? `产出 flow.${scope.expectedFlowKey}` : '登录口'
  }
  if (scope.expectedFlowKey) return `凭证 flow.${scope.expectedFlowKey}`
  return scope.profileName ? `凭证 ${scope.profileName}` : ''
}
