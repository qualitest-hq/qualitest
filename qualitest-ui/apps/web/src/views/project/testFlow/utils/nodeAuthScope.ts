/**
 * 画布 HTTP 节点的鉴权作用域：按项目 authProfiles 解析将使用 / 将产出的凭证。
 * 凭证目标从 headerValueTemplate 的 {{asset.*}} / {{flow.*}} 读取。
 * 对齐后端 ProjectAuthConfigSupport.resolveProfileId（最长 pathPrefix，未命中用数组第一条）。
 */

import { listExtractCredentialPaths } from '@/utils/flow/credentialTarget'
import { parseAuthConfig, splitPathLines } from '@/views/project/testProject/utils/projectAuthConfig'

import {
  isProfileManagedRow,
  parseCredentialDisplayPath,
  type CredentialPlaceholder,
  parseCredentialPlaceholders,
} from './authHeaderRow'

export type NodeAuthKind = 'login' | 'none' | 'inherit' | 'empty'

export interface NodeAuthScope {
  kind: NodeAuthKind
  profileName: string
  /** 期望凭证展示路径，如 asset.adminAuth.token / flow.token */
  expectedCredential: string
  /** 托管头上的凭证展示路径 */
  headerCredential: string
  /** 本节点 extracts 产出的凭证展示路径列表 */
  extractCredentials: string[]
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

function parseHeaderCredential(headers: unknown): string {
  if (!Array.isArray(headers)) return ''
  for (const row of headers) {
    if (!row || typeof row !== 'object') continue
    const r = row as Record<string, unknown>
    if (!isProfileManagedRow(r)) continue
    const path = parseCredentialDisplayPath(r.value)
    if (path) return path
  }
  return ''
}

function expectedFromProfile(profile: { valueTemplate?: string } | null | undefined): string {
  return parseCredentialDisplayPath(profile?.valueTemplate)
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
    expectedCredential: '',
    headerCredential: '',
    extractCredentials: [],
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
  const extractCredentials = listExtractCredentialPaths(input.extracts)
  const headerCredential = parseHeaderCredential(input.headers)
  const credential = findCredentialProfile(apiPath, profiles)
  if (credential) {
    const expectedCredential = expectedFromProfile(credential)
    const conflict = !!expectedCredential && !extractCredentials.includes(expectedCredential)
    return {
      kind: 'login',
      profileName: credential.name || credential.id,
      expectedCredential,
      headerCredential,
      extractCredentials,
      conflict,
      conflictReason: conflict
        ? `登录口应产出 ${expectedCredential}，当前 extracts 未写出该变量`
        : '',
    }
  }
  if (isAnonymousPrefabricated(apiPath, profiles)) {
    return {
      kind: 'none',
      profileName: '',
      expectedCredential: '',
      headerCredential,
      extractCredentials,
      conflict: false,
      conflictReason: '',
    }
  }
  const profile = resolveProfile(apiPath, profiles)
  const expectedCredential = expectedFromProfile(profile)
  const conflict =
    !!headerCredential && !!expectedCredential && headerCredential !== expectedCredential
  return {
    kind: 'inherit',
    profileName: profile?.name || profile?.id || '',
    expectedCredential,
    headerCredential,
    extractCredentials,
    conflict,
    conflictReason: conflict
      ? `托管头使用 ${headerCredential}，按 pathPrefix 应为 ${expectedCredential}`
      : '',
  }
}

/** 卡片/属性面板短文案 */
export function formatNodeAuthScopeLabel(scope: NodeAuthScope): string {
  if (scope.kind === 'empty') return ''
  if (scope.kind === 'none') return '免登'
  const expected = scope.expectedCredential || ''
  if (scope.kind === 'login') {
    return expected ? `产出 ${expected}` : '登录口'
  }
  if (expected) return `凭证 ${expected}`
  return scope.profileName ? `凭证 ${scope.profileName}` : ''
}

/** 从 Profile 值模板解析凭证占位（供外部复用） */
export function parseProfileCredentialPlaceholders(valueTemplate: unknown): CredentialPlaceholder[] {
  return parseCredentialPlaceholders(valueTemplate)
}
