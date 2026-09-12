/**
 * 测试流 AI 设计面板的用户偏好（localStorage）。
 * <p>
 * 含：Staging 确认后是否自动保存、pending 未清零时是否禁止「仅保存已确认」、是否开启全自动。
 * 全自动开启后：请求带 autopilotEnabled；后端注入 run_test_flow、素材 upsert 直写、改图隐式落盘。
 */
const AUTO_SAVE_AFTER_CONFIRM_KEY = 'qualitest.aiDesign.autoSaveAfterConfirm';
const BLOCK_WHEN_STAGING_PENDING_KEY = 'qualitest.aiDesign.blockWhenStagingPending';
const AUTOPILOT_ENABLED_KEY = 'qualitest.aiDesign.autopilotEnabled';

/** 读布尔偏好；未设置或解析失败返回 null */
function readBoolFlag(key: string): boolean | null {
  try {
    const raw = localStorage.getItem(key);
    if (raw === '1' || raw === 'true') return true;
    if (raw === '0' || raw === 'false') return false;
  } catch {
    /* ignore */
  }
  return null;
}

/** 写布尔偏好（'1' / '0'） */
function writeBoolFlag(key: string, enabled: boolean): void {
  try {
    localStorage.setItem(key, enabled ? '1' : '0');
  } catch {
    /* ignore */
  }
}

/** Staging ✓ 成功后是否自动保存 test_flow；未设置视为 false */
export function isAutoSaveAfterConfirm(): boolean {
  return readBoolFlag(AUTO_SAVE_AFTER_CONFIRM_KEY) === true;
}

export function setAutoSaveAfterConfirm(enabled: boolean): void {
  writeBoolFlag(AUTO_SAVE_AFTER_CONFIRM_KEY, enabled);
}

/**
 * 保存时若仍有未确认 Staging，是否强制先清零（隐藏「仅保存已确认」）。
 * 未设置视为 false。
 */
export function isBlockWhenStagingPending(): boolean {
  return readBoolFlag(BLOCK_WHEN_STAGING_PENDING_KEY) === true;
}

export function setBlockWhenStagingPending(enabled: boolean): void {
  writeBoolFlag(BLOCK_WHEN_STAGING_PENDING_KEY, enabled);
}

/**
 * 是否开启测试流 AI 全自动。
 * true：run_test_flow + 改图隐式落盘 + 素材 upsert 直写。
 * 未设置视为 false（半自动：Staging / 素材须人审）。
 */
export function isAutopilotEnabled(): boolean {
  return readBoolFlag(AUTOPILOT_ENABLED_KEY) === true;
}

export function setAutopilotEnabled(enabled: boolean): void {
  writeBoolFlag(AUTOPILOT_ENABLED_KEY, enabled);
}
