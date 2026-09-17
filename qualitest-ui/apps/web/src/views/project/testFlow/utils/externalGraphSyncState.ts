/**
 * 外部图同步共享状态：updateTime 去重、本端保存回声抑制、全自动会话桥接、文案。
 */
type GraphCommittedHandler = (testFlowId: string, updateTime?: string) => void

const lastAppliedByFlow = new Map<string, string>()
let suppressUntilMs = 0
let graphCommittedHandler: GraphCommittedHandler | null = null

/** 本端刚保存后短暂抑制回声同步（避免立刻再 loadFlow） */
export function suppressExternalGraphSync(ms = 2500) {
  suppressUntilMs = Date.now() + ms
}

export function isExternalGraphSyncSuppressed(): boolean {
  return Date.now() < suppressUntilMs
}

/** 是否应应用该次图更新（未处理过的 updateTime，或无时间戳） */
export function shouldApplyGraphUpdate(testFlowId: string, updateTime?: string | null): boolean {
  const id = String(testFlowId || '')
  if (!id) return false
  if (!updateTime) return true
  const prev = lastAppliedByFlow.get(id)
  if (prev && prev === updateTime) return false
  return true
}

/** 记录已应用的图版本 */
export function noteAppliedGraphUpdateTime(testFlowId: string, updateTime?: string | null) {
  const id = String(testFlowId || '')
  if (!id || !updateTime) return
  lastAppliedByFlow.set(id, updateTime)
}

export function setExternalGraphCommittedHandler(next: GraphCommittedHandler | null) {
  graphCommittedHandler = next
}

export function hasExternalGraphCommittedHandler(): boolean {
  return graphCommittedHandler != null
}

export function emitExternalGraphCommitted(testFlowId: string, updateTime?: string) {
  graphCommittedHandler?.(testFlowId, updateTime)
}

/** 同步来源短文案（Toast / 条幅共用） */
export function externalChangeSourceLabel(source?: string | null): string {
  if (source === 'mcp') return 'MCP'
  if (source === 'web-autopilot') return '全自动'
  if (source === 'web-save') return '其它端保存'
  return '外部'
}

/** 条幅括号后缀，如「（来自 MCP）」 */
export function externalChangeSourceBannerSuffix(source?: string | null): string {
  if (source === 'mcp') return '（来自 MCP）'
  if (source === 'web-autopilot') return '（来自全自动）'
  if (source === 'web-save') return '（来自其它端保存）'
  return ''
}
