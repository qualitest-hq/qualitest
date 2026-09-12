/**
 * AI Staging confirm 相关用户偏好。
 */
const AUTO_SAVE_AFTER_CONFIRM_KEY = 'qualitest.aiDesign.autoSaveAfterConfirm';
const BLOCK_WHEN_STAGING_PENDING_KEY = 'qualitest.aiDesign.blockWhenStagingPending';
const AUTOPILOT_ENABLED_KEY = 'qualitest.aiDesign.autopilotEnabled';

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

function writeBoolFlag(key: string, enabled: boolean): void {
  try {
    localStorage.setItem(key, enabled ? '1' : '0');
  } catch {
    /* ignore */
  }
}

/** 确认后是否自动保存 test_flow，未设置时为 false */
export function isAutoSaveAfterConfirm(): boolean {
  return readBoolFlag(AUTO_SAVE_AFTER_CONFIRM_KEY) === true;
}

export function setAutoSaveAfterConfirm(enabled: boolean): void {
  writeBoolFlag(AUTO_SAVE_AFTER_CONFIRM_KEY, enabled);
}

/**
 * pending>0 保存时是否强制先清零（隐藏「仅保存已确认」）。
 * 未设置时为 false。
 */
export function isBlockWhenStagingPending(): boolean {
  return readBoolFlag(BLOCK_WHEN_STAGING_PENDING_KEY) === true;
}

export function setBlockWhenStagingPending(enabled: boolean): void {
  writeBoolFlag(BLOCK_WHEN_STAGING_PENDING_KEY, enabled);
}

/**
 * 是否开启 AI 全自动（run_test_flow + 隐式落盘；upsert 直写）。
 * 未设置时为 false（半自动：Staging / 素材人审）。
 */
export function isAutopilotEnabled(): boolean {
  return readBoolFlag(AUTOPILOT_ENABLED_KEY) === true;
}

export function setAutopilotEnabled(enabled: boolean): void {
  writeBoolFlag(AUTOPILOT_ENABLED_KEY, enabled);
}
