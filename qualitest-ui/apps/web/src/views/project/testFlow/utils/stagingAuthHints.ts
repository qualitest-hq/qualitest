/**
 * Staging Diff 鉴权展示：识别托管头并生成 headers 字段标签。
 */

import { isProfileManagedRow } from './authHeaderRow'

/** 草稿是否含托管鉴权头 */
export function hasProfileManagedHeaders(draft?: Record<string, unknown>): boolean {
  const data = draft?.data as Record<string, unknown> | undefined
  const headers = Array.isArray(data?.headers) ? (data!.headers as Record<string, unknown>[]) : []
  return headers.some(isProfileManagedRow)
}

/** Diff 里 headers 字段的展示标签：有托管头时标明「按项目鉴权补全」 */
export function stagingHeadersFieldLabel(draft?: Record<string, unknown>): string {
  return hasProfileManagedHeaders(draft) ? '请求头（按项目鉴权补全）' : '请求头'
}
