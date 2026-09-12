/**
 * API 设计助手用户偏好（localStorage，key 独立于测试流面板）。
 * <p>
 * 全自动：SSE 完成后自动全选 Diff 并合并进工作台草稿；不自动保存接口库、不自动调试发送。
 * 半自动（默认）：用户勾选 Diff 后点「应用到工作台」。
 */
const AUTOPILOT_ENABLED_KEY = 'qualitest.apiAi.autopilotEnabled';

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

/**
 * 是否开启 API 助手全自动。
 * 未设置视为 false。
 */
export function isApiAiAutopilotEnabled(): boolean {
  return readBoolFlag(AUTOPILOT_ENABLED_KEY) === true;
}

export function setApiAiAutopilotEnabled(enabled: boolean): void {
  writeBoolFlag(AUTOPILOT_ENABLED_KEY, enabled);
}
