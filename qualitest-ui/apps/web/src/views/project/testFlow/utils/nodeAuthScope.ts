/**
 * 画布 HTTP 节点的鉴权作用域：按项目 authProfiles 解析本节点将使用 / 将产出的凭证。
 * 凭证目标从 headerValueTemplate 里的 {{asset.*}} / {{flow.*}} 读取。
 *
 * 选端规则：最长 pathPrefix 命中优先；多端都未命中则不加托管头（返回空凭证期望）；
 * 项目只有一套 Profile 时，未命中前缀仍用该套。
 * 登录口：extracts 写出目标命中某 Profile 托管头 → kind=login；空 extracts 不当登录口、不因缺写 conflict。
 * 免登口（预制 api authMode=none）：kind=none。
 * 普通 inherit：托管头凭证路径若与 pathPrefix 命中 Profile 的模板不同，则 conflict。
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
  /** 当前节点托管头上的凭证展示路径 */
  headerCredential: string
  /** 本节点 extracts 产出的凭证展示路径列表 */
  extractCredentials: string[]
  /** 是否存在凭证目标冲突或缺写 */
  conflict: boolean
  /** conflict 时的人类可读原因 */
  conflictReason: string
}

/** 规范化接口路径：补前导 /、去掉多余尾斜杠。 */
function normalizeApiPath(apiPath: string) {
  let p = String(apiPath || '').trim()
  if (!p) return '/'
  if (!p.startsWith('/')) p = '/' + p
  while (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1)
  return p
}

/** 规范化 pathPrefix：补前导 / 与尾斜杠；空或单独 / 视为无效。 */
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

/** 从节点 headers 里找第一条 profileManaged 行，解析其值模板中的凭证路径。 */
function parseHeaderCredential(headers: unknown): string {
  if (!Array.isArray(headers)) return ''
  for (const row of headers) {
    if (!row || typeof row !== 'object') continue
    const r = row as Record<string, unknown>
    if (!isProfileManagedRow(r)) continue
    const path = parseCredentialDisplayPath(r.value)
    if (path) return path
    return ''
  }
  return ''
}

function expectedFromProfile(profile: { valueTemplate?: string } | null | undefined): string {
  return parseCredentialDisplayPath(profile?.valueTemplate)
}

/**
 * 按最长 pathPrefix 选 Profile；都未命中时，仅一套 Profile 则回落该套，否则 null。
 */
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
  return best || (profiles.length === 1 ? profiles[0] : null) || null
}

/**
 * extracts 产出路径是否命中某 Profile 托管头；命中则返回该 Profile（先扫完取第一条相交）。
 */
function findLoginProfileByExtracts(
  extractCredentials: string[],
  profiles: ReturnType<typeof parseAuthConfig>['profiles'],
) {
  if (!extractCredentials.length) return null
  for (const p of profiles) {
    const expected = expectedFromProfile(p)
    if (expected && extractCredentials.includes(expected)) {
      return p
    }
  }
  return null
}

/** 路径是否落在某端预制免登接口上（authMode=none）。 */
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

/** 空作用域：项目无 authProfiles 或无法解析时使用。 */
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
 * 解析节点将使用或产出的凭证变量，供卡片角标与属性面板展示。
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
  const loginProfile = findLoginProfileByExtracts(extractCredentials, profiles)
  if (loginProfile) {
    const expectedCredential = expectedFromProfile(loginProfile)
    return {
      kind: 'login',
      profileName: loginProfile.name || loginProfile.id,
      expectedCredential,
      headerCredential,
      extractCredentials,
      conflict: false,
      conflictReason: '',
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

/** 卡片/属性面板短文案（如「产出 asset.x.token」「凭证 asset.x.token」「免登」）。 */
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

/** 从 Profile 值模板解析凭证占位（asset.* / flow.*）。 */
export function parseProfileCredentialPlaceholders(valueTemplate: unknown): CredentialPlaceholder[] {
  return parseCredentialPlaceholders(valueTemplate)
}
