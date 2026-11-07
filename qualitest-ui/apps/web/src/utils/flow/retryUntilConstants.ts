/**
 * HTTP 节点「重试直到通过」（data.retryUntil）的数值上下限、缺省值与时间线文案。
 *
 * 字段含义：
 * - maxAttempts：本节点最多发出几次 HTTP 请求
 * - intervalMs：相邻两次请求之间的等待毫秒
 * - until：可选通过条件规则列表（left / operator / right）
 */

/** 允许配置的最多请求次数上限 */
export const RETRY_UNTIL_MAX_ATTEMPTS = 60

/** 允许配置的请求间隔上限（毫秒） */
export const RETRY_UNTIL_MAX_INTERVAL_MS = 60_000

/** 未填写时的默认最多请求次数（只请求一次） */
export const RETRY_UNTIL_DEFAULT_MAX_ATTEMPTS = 1

/** 未填写时的默认请求间隔（毫秒） */
export const RETRY_UNTIL_DEFAULT_INTERVAL_MS = 1_000

/**
 * 生成运行时间线 HTTP 步骤摘要末尾的次数文案。
 * 实际次数不大于 1 时返回空串；否则返回「 · 第 N/M 次」。
 *
 * @param attempts 实际发出的请求次数
 * @param maxAttempts 配置的次数上限；缺省时用实际次数作分母
 */
export function formatRetryAttemptSuffix(
  attempts: number | null | undefined,
  maxAttempts?: number | null,
): string {
  const n = Number(attempts)
  if (!Number.isFinite(n) || n <= 1) return ''
  const max = maxAttempts != null && Number.isFinite(Number(maxAttempts)) ? maxAttempts : attempts
  return ` · 第 ${attempts}/${max} 次`
}
