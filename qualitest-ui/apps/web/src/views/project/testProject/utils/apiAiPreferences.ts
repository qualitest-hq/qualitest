/**
 * AI API 助手用户偏好（与造流 aiDesignPreferences 分 key，互不影响）。
 */
const AUTOPILOT_ENABLED_KEY = 'qualitest.apiAi.autopilotEnabled';

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

/**
 * 是否开启 API 助手全自动（SSE 完成后自动应用到工作台草稿）。
 * 未设置时为 false（半自动：Diff 勾选后应用）。不自动保存接口库。
 */
export function isApiAiAutopilotEnabled(): boolean {
  return readBoolFlag(AUTOPILOT_ENABLED_KEY) === true;
}

export function setApiAiAutopilotEnabled(enabled: boolean): void {
  writeBoolFlag(AUTOPILOT_ENABLED_KEY, enabled);
}
