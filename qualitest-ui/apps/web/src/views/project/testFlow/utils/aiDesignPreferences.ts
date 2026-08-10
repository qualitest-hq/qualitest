/**
 * AI Staging confirm 相关用户偏好。
 */
const AUTO_SAVE_AFTER_CONFIRM_KEY = 'qualitest.aiDesign.autoSaveAfterConfirm';
const LEGACY_AUTO_SAVE_AFTER_MERGE_KEY = 'qualitest.aiDesign.autoSaveAfterMerge';
const BLOCK_WHEN_STAGING_PENDING_KEY = 'qualitest.aiDesign.blockWhenStagingPending';

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
  const direct = readBoolFlag(AUTO_SAVE_AFTER_CONFIRM_KEY);
  if (direct != null) return direct;
  const legacy = readBoolFlag(LEGACY_AUTO_SAVE_AFTER_MERGE_KEY);
  return legacy === true;
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

/** @deprecated 旧合并链路别名，Step 9 删除 */
export function isAutoSaveAfterMerge(): boolean {
  return isAutoSaveAfterConfirm();
}

/** @deprecated 旧合并链路别名，Step 9 删除 */
export function setAutoSaveAfterMerge(enabled: boolean): void {
  setAutoSaveAfterConfirm(enabled);
}
