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

/**
 * 抢锁（或续期）请求返回后，判断是否留下这把锁并开始续期。
 * 请求期间锁需求已作废（序号变了），或画布已不需要持锁，返回 false，调用方应立即放锁。
 */
export function shouldKeepLeaseAfterAcquire(params: {
  /** 发起请求时的锁需求序号 */
  epochAtStart: number
  /** 请求返回时的锁需求序号 */
  currentEpoch: number
  /** 请求返回时画布是否仍需要持锁 */
  needsLongLease: boolean
}): boolean {
  if (params.epochAtStart !== params.currentEpoch) return false
  if (!params.needsLongLease) return false
  return true
}

/**
 * 锁需求：当前想锁哪条流、要不要锁。
 * 画布状态变化时更新它，抢锁与释放按它执行。
 */
export type FlowEditLeaseIntent = {
  /** 目标测试流 id；空串表示没有打开任何流 */
  flowId: string
  /** 是否需要持锁 */
  wantLongLease: boolean
  /**
   * 序号；切换测试流或从「要锁」变成「不要锁」时加一。
   * 抢锁请求返回时序号已变，说明这次抢到的锁不该留。
   */
  epoch: number
}

/** 初始锁需求：无流、不要锁、序号为 0 */
export function emptyFlowEditLeaseIntent(): FlowEditLeaseIntent {
  return { flowId: '', wantLongLease: false, epoch: 0 }
}

/**
 * 用最新的画布状态生成新的锁需求。
 * 流 id 去掉首尾空白，为空时一律视为不要锁；切换测试流或放弃持锁时序号加一，其余情况序号不变。
 */
export function advanceFlowEditLeaseIntent(
  prev: FlowEditLeaseIntent,
  next: { flowId: string; wantLongLease: boolean },
): FlowEditLeaseIntent {
  const flowId = next.flowId != null ? String(next.flowId).trim() : ''
  const wantLongLease = Boolean(next.wantLongLease) && !!flowId
  const flowChanged = prev.flowId !== flowId
  const droppedWant = prev.wantLongLease && !wantLongLease
  const epoch = flowChanged || droppedWant ? prev.epoch + 1 : prev.epoch
  return { flowId, wantLongLease, epoch }
}

/**
 * 写锁处理的下一步动作：
 * - idle：保持现状
 * - acquire：抢指定流的锁，带上发起时的锁需求序号
 * - release：释放指定流的锁
 */
export type FlowEditLeaseStep =
  | { type: 'idle' }
  | { type: 'acquire'; flowId: string; epoch: number }
  | { type: 'release'; flowId: string }

/**
 * 对比锁需求和本页实际持锁情况，算出下一步动作。
 * 判断顺序：
 * 1. 正持有别的流的锁：先释放它；
 * 2. 不需要锁：持锁就释放；没持锁但本地还留着凭证，也释放；否则保持现状；
 * 3. 需要锁且已持有：保持现状；
 * 4. 需要锁但未持有：抢锁。
 */
export function nextFlowEditLeaseStep(params: {
  /** 当前锁需求 */
  intent: FlowEditLeaseIntent
  /** 本页是否正持有写锁并在续期 */
  holding: boolean
  /** 本页正在续期的测试流 id；未持锁为空串 */
  holdingFlowId: string
  /** 目标测试流在本地是否还存有锁凭证 */
  hasTokenForIntentFlow: boolean
}): FlowEditLeaseStep {
  const intentFlowId = params.intent.flowId
  const holdingFlowId = params.holdingFlowId != null ? String(params.holdingFlowId).trim() : ''

  // 正持有别的流的锁：先释放
  if (params.holding && holdingFlowId && holdingFlowId !== intentFlowId) {
    return { type: 'release', flowId: holdingFlowId }
  }

  // 不需要锁：释放正在持有的锁，或清掉本地残留凭证对应的锁
  if (!params.intent.wantLongLease || !intentFlowId) {
    if (params.holding && holdingFlowId) {
      return { type: 'release', flowId: holdingFlowId }
    }
    if (intentFlowId && params.hasTokenForIntentFlow) {
      return { type: 'release', flowId: intentFlowId }
    }
    return { type: 'idle' }
  }

  // 需要锁且已持有：保持现状
  if (params.holding && params.hasTokenForIntentFlow) {
    return { type: 'idle' }
  }

  // 需要锁但未持有：抢锁

  return { type: 'acquire', flowId: intentFlowId, epoch: params.intent.epoch }
}
