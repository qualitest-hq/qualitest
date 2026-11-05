/**
 * 测试流 Run 与步骤状态 code、中文标签与展示辅助。
 * bypassed / continued / continued_failed 为失败后继续阶段的步骤态；
 * skipped 仍表示人工跳过或续跑 skip。
 */

/** Run 级状态 code */
export const RUN_RECORD_STATUSES = [
  'passed',
  'failed',
  'running',
  'paused',
  'aborted',
  'cancelled',
] as const;

export type RunRecordStatus = (typeof RUN_RECORD_STATUSES)[number];

/**
 * 单步状态 code。
 * bypassed：失败后路过未执行；continued：失败后仍执行且成功；continued_failed：失败后仍执行但本步失败。
 */
export const RUN_STEP_STATUSES = [
  'passed',
  'failed',
  'skipped',
  'paused',
  'bypassed',
  'continued',
  'continued_failed',
] as const;

export type RunStepStatus = (typeof RUN_STEP_STATUSES)[number];

/** Run 已结束（含暂停等待续跑）的状态集合 */
export const TERMINAL_RUN_STATUSES = new Set<RunRecordStatus>([
  'passed',
  'failed',
  'paused',
  'aborted',
  'cancelled',
]);

/** 状态中文标签（Run 列表、步骤时间线徽章） */
export const RUN_STATUS_LABEL: Record<string, string> = {
  passed: '通过',
  failed: '失败',
  running: '运行中',
  paused: '已暂停',
  aborted: '已中止',
  cancelled: '已取消',
  skipped: '跳过',
  bypassed: '已绕过',
  continued: '失败后继续',
  continued_failed: '继续失败',
};

/** Run 级状态对应的 Element Plus Tag type */
export const RUN_STATUS_TAG_TYPE: Record<RunRecordStatus, string> = {
  passed: 'success',
  failed: 'danger',
  running: 'warning',
  paused: 'warning',
  aborted: 'info',
  cancelled: 'info',
};

/** 状态 code 转中文；空则 '-'，未知则原样返回 */
export function runStatusLabel(status: string | null | undefined): string {
  if (!status) return '-';
  return (RUN_STATUS_LABEL as Record<string, string>)[status] || status;
}

/** Run 状态转 Tag type；未知为 info */
export function runStatusTagType(status: string | null | undefined): string {
  if (!status) return 'info';
  return (RUN_STATUS_TAG_TYPE as Record<string, string>)[status] || 'info';
}

/**
 * 是否应按失败对待（失败摘要、失败卡片）。
 * failed 为业务首错；continued_failed 为失败后继续阶段本步又失败。
 */
export function isFailureLikeStatus(status: string | null | undefined): boolean {
  return status === 'failed' || status === 'continued_failed';
}

/**
 * 将步骤 status 映射为画布访问着色键。
 * 失败后继续三态保留原值；其余通过类归为 passed。
 */
export function toCanvasVisitStatus(
  status: string | null | undefined,
): 'passed' | 'failed' | 'skipped' | 'bypassed' | 'continued' | 'continued_failed' {
  if (status === 'failed' || status === 'continued_failed') return status;
  if (status === 'skipped' || status === 'bypassed') return status;
  if (status === 'continued') return 'continued';
  return 'passed';
}

/** 运行触发方式 code */
export const RUN_TRIGGER_TYPES = ['manual', 'ai', 'ci', 'schedule', 'suite'] as const;

export type RunTriggerType = (typeof RUN_TRIGGER_TYPES)[number];

/** 触发方式中文标签 */
export const RUN_TRIGGER_LABEL: Record<RunTriggerType, string> = {
  manual: '手动',
  ai: 'AI 跑流',
  ci: 'CI',
  schedule: '定时',
  suite: '批量',
};

/** 触发方式展示文案；未知 code 原样返回 */
export function runTriggerLabel(triggerType: string | null | undefined): string {
  if (!triggerType?.trim()) return '';
  const key = triggerType.trim() as RunTriggerType;
  return RUN_TRIGGER_LABEL[key] || triggerType.trim();
}
