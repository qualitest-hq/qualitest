/**
 * 测试流写锁的本标签页凭证（token）。
 *
 * 服务端用 Redis 保存真正的写锁；本模块只保存「本标签持有哪把租约」的 token，
 * 供心跳续期、保存请求头、主动释锁使用。
 * token 按测试流 id 写入 sessionStorage，同标签刷新后仍可读出并续约。
 */

/** 写图请求头中携带的租约 token 字段名 */
export const FLOW_EDIT_LEASE_HEADER = 'X-Flow-Edit-Lease'

/** sessionStorage 键前缀，后接测试流 id */
const STORAGE_PREFIX = 'qualitest:flow-edit-lease:'

/** 内存中当前流 id */
let currentFlowId: string | null = null
/** 内存中当前租约 token */
let currentToken: string | null = null

/** 拼出某测试流在 sessionStorage 中的键名 */
function storageKey(flowId: string) {
  return STORAGE_PREFIX + flowId
}

/** 去掉首尾空白；空值得到空串 */
function normalizeFlowId(flowId: string | undefined | null): string {
  return flowId != null ? String(flowId).trim() : ''
}

/**
 * 从 sessionStorage 读取指定流的 token。
 * 读失败（如禁用存储）时返回 null。
 */
function readSession(flowId: string): string | null {
  try {
    const raw = sessionStorage.getItem(storageKey(flowId))
    const t = raw != null ? String(raw).trim() : ''
    return t || null
  } catch {
    return null
  }
}

/**
 * 写入或删除 sessionStorage 中的 token。
 * token 为空则删除该键；写失败时忽略（内存侧仍可保留凭证）。
 */
function writeSession(flowId: string, token: string | null) {
  try {
    if (!token) {
      sessionStorage.removeItem(storageKey(flowId))
    } else {
      sessionStorage.setItem(storageKey(flowId), token)
    }
  } catch {
    // 存储不可用时仍依赖内存中的 token
  }
}

/**
 * 设置指定测试流的租约 token。
 * token 为空时清除该流在内存与 sessionStorage 中的记录。
 *
 * @param flowId 测试流 id
 * @param token 租约凭证；传 null 或空串表示清空
 */
export function setFlowEditLeaseToken(flowId: string, token: string | null) {
  const id = normalizeFlowId(flowId)
  if (!id) return
  const next = token != null ? String(token).trim() : ''
  if (!next) {
    if (currentFlowId === id) {
      currentFlowId = null
      currentToken = null
    }
    writeSession(id, null)
    return
  }
  currentFlowId = id
  currentToken = next
  writeSession(id, next)
}

/**
 * 读取指定测试流的租约 token。
 * 优先返回内存值；内存没有则读 sessionStorage，读到后写回内存。
 * 两边都没有则返回 null，并清掉过期的内存缓存。
 *
 * @param flowId 测试流 id
 * @return 租约 token，没有则 null
 */
export function getFlowEditLeaseToken(flowId: string): string | null {
  const id = normalizeFlowId(flowId)
  if (!id) return null
  if (currentFlowId === id && currentToken) {
    return currentToken
  }
  const fromSs = readSession(id)
  if (fromSs) {
    currentFlowId = id
    currentToken = fromSs
    return fromSs
  }
  if (currentFlowId === id) {
    currentFlowId = null
    currentToken = null
  }
  return null
}

/**
 * 将 lockHeldBy 转为顶栏短名。
 * web:alice:uuid → alice；mcp:… → MCP；其它 →「其他会话」。
 */
export function formatFlowEditLeaseHolder(lockHeldBy: string | null | undefined): string {
  const raw = lockHeldBy != null ? String(lockHeldBy).trim() : ''
  if (!raw) return '其他会话'
  const parts = raw.split(':')
  if (parts[0] === 'mcp') return 'MCP'
  if (parts.length >= 3 && parts[0] === 'web' && parts[1]) {
    return parts[1]
  }
  return '其他会话'
}
