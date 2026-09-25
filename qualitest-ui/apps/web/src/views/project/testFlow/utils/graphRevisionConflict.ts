/**
 * 解析保存接口的图版本冲突：从错误对象或文案取出库中当前图版本号。
 */

/** 冲突时返回库中当前版本；无法解析具体数字时返回 -1；非冲突返回 null */
export function parseGraphRevisionConflict(error: unknown): number | null {
  if (error == null) return null

  // 优先读错误对象上的版本冲突标记与当前版本号
  const asRecord = error as {
    message?: string
    graphRevision?: unknown
    revisionConflict?: unknown
  }
  if (asRecord.revisionConflict === true || asRecord.graphRevision != null) {
    const n = Number(asRecord.graphRevision)
    if (Number.isFinite(n)) return n
    return -1
  }

  const msg =
    error instanceof Error
      ? error.message
      : typeof error === 'string'
        ? error
        : typeof asRecord.message === 'string'
          ? asRecord.message
          : ''

  if (!msg) return null

  const fromField = msg.match(/graphRevision\s*[=:：]\s*(\d+)/i)
  if (fromField) return Number(fromField[1])

  if (/版本冲突|revisionConflict/i.test(msg)) return -1
  return null
}

/** 从保存成功响应中读取新的图版本号；未回传则返回 null */
export function extractGraphRevisionFromResponse(res: unknown): number | null {
  if (res == null || typeof res !== 'object') return null
  const root = res as Record<string, unknown>
  const data = root.data
  if (data != null && typeof data === 'object' && !Array.isArray(data)) {
    const n = Number((data as Record<string, unknown>).graphRevision)
    if (Number.isFinite(n)) return n
  }
  const top = Number(root.graphRevision)
  if (Number.isFinite(top)) return top
  return null
}
