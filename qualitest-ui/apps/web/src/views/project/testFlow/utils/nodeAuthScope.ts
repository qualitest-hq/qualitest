/**
 * 画布 HTTP 节点鉴权展示计算。
 *
 * 直接产出卡片短文案 label：
 * - apiAuthMode=none →「免登」
 * - path 命中 Profile 头模板 →「头 asset.x.token」
 * - 仅有鉴权错误 →「鉴权风险」
 *
 * 凭证写出走节点自带的 extracts chip，这里不再重复「写出 …」。
 * 不读节点 headers 上的 profileManaged（设计期不落托管头，开跑时才补）。
 * conflict 只来自传进来的鉴权类画布错误（error 级）。
 */

import { parseJsonMaybe } from '@/views/project/testProject/utils/projectAuthConfig'

import {
  parseCredentialDisplayPath,
  type CredentialPlaceholder,
  parseCredentialPlaceholders,
} from './authHeaderRow'
import type { FlowCanvasValidationIssue } from './flowValidationIssues'
import { isAuthRiskMessage } from './runReadiness'

export interface NodeAuthScope {
  /** 卡片/属性面板短文案；无信息时为空串 */
  label: string
  profileName: string
  /** 期望/展示用占位路径，如 asset.adminAuth.token / flow.token */
  expectedCredential: string
  /** 本节点是否挂有鉴权类画布错误 */
  conflict: boolean
  /** conflict 时去掉 AUTH_ 码前缀后的可读原因 */
  conflictReason: string
}

/** 去掉 message 开头的 AUTH_XXX: 前缀，只留人类可读部分 */
function stripAuthCodePrefix(message: string): string {
  const text = String(message || '').trim()
  const idx = text.indexOf(':')
  if (idx > 0 && /^AUTH_[A-Z0-9_]+$/.test(text.slice(0, idx))) {
    return text.slice(idx + 1).trim() || text
  }
  return text
}

/** 是否为鉴权相关画布 issue（排除 HTTP 必填类） */
function isAuthCanvasIssue(issue: FlowCanvasValidationIssue): boolean {
  const code = String(issue.code || '').trim()
  if (code === 'RUN_READINESS_AUTH') return true
  if (code === 'RUN_READINESS_HTTP_PARAM') return false
  if (code.startsWith('AUTH_LOGIN_')) return true
  return isAuthRiskMessage(String(issue.message || ''))
}

function buildScope(partial: Partial<NodeAuthScope> & { label?: string }): NodeAuthScope {
  const expected = partial.expectedCredential || ''
  const profileName = partial.profileName || ''
  const conflict = !!partial.conflict
  let label = partial.label
  if (label === undefined) {
    if (expected) {
      label = `头 ${expected}`
    } else if (conflict) {
      label = '鉴权风险'
    } else if (profileName) {
      label = `头 ${profileName}`
    } else {
      label = ''
    }
  }
  return {
    label,
    profileName,
    expectedCredential: expected,
    conflict,
    conflictReason: partial.conflictReason || '',
  }
}

/** 空作用域：无文案、无鉴权错误 */
export function emptyNodeAuthScope(): NodeAuthScope {
  return buildScope({
    label: '',
    profileName: '',
    expectedCredential: '',
    conflict: false,
    conflictReason: '',
  })
}

/**
 * 按最长 pathPrefix 命中项目 Profile，取出头值模板里的凭证展示路径与 Profile 名。
 * 单独的「/」前缀忽略；path 为空则不命中。
 */
export function resolveProfileCredentialFromProjectAuth(
  projectAuthConfig: unknown,
  apiPath: unknown,
): { profileName: string; credentialPath: string } {
  const path = String(apiPath || '').trim()
  if (!path) return { profileName: '', credentialPath: '' }
  const root = parseJsonMaybe(projectAuthConfig)
  const profiles = Array.isArray(root?.authProfiles) ? root.authProfiles : []
  let best: { name: string; template: string; len: number } | null = null
  for (const p of profiles) {
    if (!p || typeof p !== 'object') continue
    const prefixes = Array.isArray(p.match?.pathPrefix) ? p.match.pathPrefix : []
    for (const raw of prefixes) {
      const prefix = String(raw || '').trim()
      if (!prefix || prefix === '/') continue
      if (!path.startsWith(prefix)) continue
      if (!best || prefix.length > best.len) {
        best = {
          name: String(p.name || p.id || '').trim(),
          template: String(p.headerValueTemplate || '').trim(),
          len: prefix.length,
        }
      }
    }
  }
  if (!best) return { profileName: '', credentialPath: '' }
  return {
    profileName: best.name,
    credentialPath: parseCredentialDisplayPath(best.template),
  }
}

/**
 * 解析节点鉴权展示结果（含 label）。
 * conflict 仅统计 error 级、且挂到本节点（或未标节点）的鉴权类 issue。
 */
export function resolveNodeAuthScope(input: {
  nodeId?: string
  /** 绑接口时写入节点的 auth.mode */
  apiAuthMode?: unknown
  /** 绑接口时写入节点的 path，用于命中 Profile */
  apiPath?: unknown
  projectAuthConfig?: unknown
  authIssues?: FlowCanvasValidationIssue[]
}): NodeAuthScope {
  const nodeId = String(input.nodeId || '').trim()
  const apiAuthMode = String(input.apiAuthMode || '').trim().toLowerCase()
  const related = (input.authIssues || []).filter((issue) => {
    if (issue.level !== 'error') return false
    if (!isAuthCanvasIssue(issue)) return false
    if (!nodeId) return true
    const ids = issue.nodeIds || []
    if (!ids.length) return true
    return ids.includes(nodeId)
  })
  const conflict = related.length > 0
  const conflictReason = conflict ? stripAuthCodePrefix(related[0].message) : ''
  const profileHit = resolveProfileCredentialFromProjectAuth(
    input.projectAuthConfig,
    input.apiPath,
  )

  if (apiAuthMode === 'none') {
    return buildScope({
      label: '免登',
      conflict,
      conflictReason,
    })
  }
  if (apiAuthMode === 'inherit' || apiAuthMode === 'override' || profileHit.credentialPath) {
    return buildScope({
      expectedCredential: profileHit.credentialPath,
      profileName: profileHit.profileName,
      conflict,
      conflictReason,
    })
  }
  if (conflict) {
    return buildScope({
      label: '鉴权风险',
      conflict: true,
      conflictReason,
    })
  }
  return emptyNodeAuthScope()
}

/** 从 Profile 头值模板解析凭证占位（asset.* / flow.*） */
export function parseProfileCredentialPlaceholders(valueTemplate: unknown): CredentialPlaceholder[] {
  return parseCredentialPlaceholders(valueTemplate)
}
