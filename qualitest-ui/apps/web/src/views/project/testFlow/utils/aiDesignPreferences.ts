/**
 * AI Staging confirm 相关用户偏好。
 */
const AUTO_SAVE_AFTER_CONFIRM_KEY = 'qualitest.aiDesign.autoSaveAfterConfirm';
const LEGACY_AUTO_SAVE_AFTER_MERGE_KEY = 'qualitest.aiDesign.autoSaveAfterMerge';

/** 确认后是否自动保存 test_flow，未设置时为 false */
export function isAutoSaveAfterConfirm(): boolean {
  try {
    const raw = localStorage.getItem(AUTO_SAVE_AFTER_CONFIRM_KEY);
    if (raw === '1' || raw === 'true') return true;
    if (raw === '0' || raw === 'false') return false;
    const legacy = localStorage.getItem(LEGACY_AUTO_SAVE_AFTER_MERGE_KEY);
    return legacy === '1' || legacy === 'true';
  } catch {
    /* ignore */
  }
  return false;
}

export function setAutoSaveAfterConfirm(enabled: boolean): void {
  try {
    localStorage.setItem(AUTO_SAVE_AFTER_CONFIRM_KEY, enabled ? '1' : '0');
  } catch {
    /* ignore */
  }
}

/** @deprecated 旧合并链路别名，Step 9 删除 */
export function isAutoSaveAfterMerge(): boolean {
  return isAutoSaveAfterConfirm();
}

/** @deprecated 旧合并链路别名，Step 9 删除 */
export function setAutoSaveAfterMerge(enabled: boolean): void {
  setAutoSaveAfterConfirm(enabled);
}
