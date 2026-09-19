/**
 * 测试流 Run / 步骤状态（与后端 RunStatus.code 对应）。
 * 页面展示文案硬编码于此，不接系统字典。
 */

/** Run 状态 code */
export const RUN_RECORD_STATUSES = [
  'passed',
  'failed',
  'running',
  'paused',
  'aborted',
  'cancelled',
] as const;

export type RunRecordStatus = (typeof RUN_RECORD_STATUSES)[number];

/** 单步状态 code */
export const RUN_STEP_STATUSES = ['passed', 'failed', 'skipped', 'paused'] as const;

export type RunStepStatus = (typeof RUN_STEP_STATUSES)[number];

/** Run 终态（含 paused：等待 resume） */
export const TERMINAL_RUN_STATUSES = new Set<RunRecordStatus>([
  'passed',
  'failed',
  'paused',
  'aborted',
  'cancelled',
]);

/** 中文标签（列表 / Tag） */
export const RUN_STATUS_LABEL: Record<RunRecordStatus, string> = {
  passed: '通过',
  failed: '失败',
  running: '运行中',
  paused: '已暂停',
  aborted: '已中止',
  cancelled: '已取消',
};

/** Element Plus Tag type */
export const RUN_STATUS_TAG_TYPE: Record<RunRecordStatus, string> = {
  passed: 'success',
  failed: 'danger',
  running: 'warning',
  paused: 'warning',
  aborted: 'info',
  cancelled: 'info',
};

export function runStatusLabel(status: string | null | undefined): string {
  if (!status) return '-';
  return (RUN_STATUS_LABEL as Record<string, string>)[status] || status;
}

export function runStatusTagType(status: string | null | undefined): string {
  if (!status) return 'info';
  return (RUN_STATUS_TAG_TYPE as Record<string, string>)[status] || 'info';
}
