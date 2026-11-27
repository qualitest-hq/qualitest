/**
 * 画布 HTTP 节点鉴权展示：凭证路径来自节点 headers/extracts；
 * conflict 只认画布校验里的 AUTH_* / RUN_READINESS_AUTH issues（服务端权威）。
 * 不再本地推演 pathPrefix / Profile 匹配。
 */

import { listExtractCredentialPaths } from '@/utils/flow/credentialTarget'

import {
  isProfileManagedRow,
  parseCredentialDisplayPath,
  type CredentialPlaceholder,
  parseCredentialPlaceholders,
} from './authHeaderRow'
import type { FlowCanvasValidationIssue } from './flowValidationIssues'
import { isAuthRiskMessage } from './runReadiness'

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
  /** 是否存在服务端鉴权硬拦/预警 */
  conflict: boolean
  /** conflict 时的人类可读原因 */
  conflictReason: string
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

function stripAuthCodePrefix(message: string): string {
  const text = String(message || '').trim()
  const idx = text.indexOf(':')
  if (idx > 0 && /^AUTH_[A-Z0-9_]+$/.test(text.slice(0, idx))) {
    return text.slice(idx + 1).trim() || text
  }
  return text
}

function isAuthCanvasIssue(issue: FlowCanvasValidationIssue): boolean {
  const code = String(issue.code || '').trim()
  if (code === 'RUN_READINESS_AUTH') return true
  if (code.startsWith('AUTH_')) return true
  return isAuthRiskMessage(String(issue.message || ''))
}

/** 空作用域：无凭证展示、无鉴权 issue。 */
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
 * 解析节点鉴权展示。
 * conflict 仅来自 authIssues（通常为 validate?stage=canvas 中与本节点相关的 AUTH 问题）。
 */
export function resolveNodeAuthScope(input: {
  nodeId?: string
  headers?: unknown
  extracts?: unknown
  authIssues?: FlowCanvasValidationIssue[]
}): NodeAuthScope {
  const nodeId = String(input.nodeId || '').trim()
  const extractCredentials = listExtractCredentialPaths(input.extracts)
  const headerCredential = parseHeaderCredential(input.headers)
  const related = (input.authIssues || []).filter((issue) => {
    // 仅生效 ERROR 当鉴权 conflict；WARNING 不进硬样式
    if (issue.level !== 'error') return false
    if (!isAuthCanvasIssue(issue)) return false
    if (!nodeId) return true
    const ids = issue.nodeIds || []
    if (!ids.length) return true
    return ids.includes(nodeId)
  })
  const conflict = related.length > 0
  const conflictReason = conflict ? stripAuthCodePrefix(related[0].message) : ''

  if (extractCredentials.length) {
    return {
      kind: 'login',
      profileName: '',
      expectedCredential: extractCredentials[0] || '',
      headerCredential,
      extractCredentials,
      conflict,
      conflictReason,
    }
  }
  if (headerCredential) {
    return {
      kind: 'inherit',
      profileName: '',
      expectedCredential: headerCredential,
      headerCredential,
      extractCredentials,
      conflict,
      conflictReason,
    }
  }
  if (conflict) {
    return {
      kind: 'inherit',
      profileName: '',
      expectedCredential: '',
      headerCredential: '',
      extractCredentials,
      conflict: true,
      conflictReason,
    }
  }
  return emptyNodeAuthScope()
}

/** 卡片/属性面板短文案（如「产出 asset.x.token」「凭证 asset.x.token」）。 */
export function formatNodeAuthScopeLabel(scope: NodeAuthScope): string {
  if (scope.kind === 'empty') return ''
  if (scope.kind === 'none') return '免登'
  const expected = scope.expectedCredential || ''
  if (scope.kind === 'login') {
    return expected ? `产出 ${expected}` : '登录口'
  }
  if (expected) return `凭证 ${expected}`
  if (scope.conflict) return '鉴权风险'
  return scope.profileName ? `凭证 ${scope.profileName}` : ''
}

/** 从 Profile 值模板解析凭证占位（asset.* / flow.*）。 */
export function parseProfileCredentialPlaceholders(valueTemplate: unknown): CredentialPlaceholder[] {
  return parseCredentialPlaceholders(valueTemplate)
}
