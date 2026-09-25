/**
 * 外部图同步共享状态：updateTime 去重、本端保存回声抑制、订阅活跃标记、来源文案。
 */

const lastAppliedByFlow = new Map<string, string>()
let suppressUntilMs = 0
/** 画布已订阅外部变更长连接时为 true；全自动落库据此跳过整图重载 */
let externalGraphSyncListening = false
/** 本端保存成功后清掉误报的「其它端保存」条幅 */
let localWebSaveAckHandler: (() => void) | null = null

/** 变更来源 → 短名 / 条幅后缀 */
const SOURCE_LABELS: Record<string, { short: string; banner: string }> = {
  mcp: { short: 'MCP', banner: '（来自 MCP）' },
  'web-autopilot': { short: '全自动', banner: '（来自全自动）' },
  'web-save': { short: '其它端保存', banner: '（来自其它端保存）' },
}

/**
 * 本端即将/正在保存时抑制回声同步。
 * 须在发保存请求前调用，避免服务端变更通知在 HTTP 返回前抢先到达。
 */
export function suppressExternalGraphSync(ms = 5000) {
  suppressUntilMs = Math.max(suppressUntilMs, Date.now() + ms)
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

/** 标记是否已订阅外部变更长连接（有流 id 且启用时为 true） */
export function setExternalGraphSyncListening(listening: boolean) {
  externalGraphSyncListening = listening
}

/** 画布是否正在收外部改图通知；全自动落库据此决定是否跳过整图重载 */
export function isExternalGraphSyncListening(): boolean {
  return externalGraphSyncListening
}

/** 由外部同步模块注册：本端保存成功后清除误报条幅 */
export function setLocalWebSaveAckHandler(next: (() => void) | null) {
  localWebSaveAckHandler = next
}

/** 本端保存成功：确认本端回声，清掉「其它端保存」条幅 */
export function acknowledgeLocalWebSave() {
  localWebSaveAckHandler?.()
}

/** 同步来源短文案（Toast / 顶栏短暂提示共用） */
export function externalChangeSourceLabel(source?: string | null): string {
  if (source == null) return '外部'
  return SOURCE_LABELS[source]?.short ?? '外部'
}

/** 条幅括号后缀，如「（来自 MCP）」 */
export function externalChangeSourceBannerSuffix(source?: string | null): string {
  if (source == null) return ''
  return SOURCE_LABELS[source]?.banner ?? ''
}
