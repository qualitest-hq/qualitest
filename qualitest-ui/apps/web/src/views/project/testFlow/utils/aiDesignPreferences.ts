/**
 * 测试流 AI 助手面板的用户偏好（localStorage）。
 *
 * 目前只持久化「全自动」开关：
 * - 开：发送设计请求时带 autopilotEnabled=true；本轮可改图后直接写库、素材 upsert 直接写库，并可调用 run_test_flow。
 * - 关（半自动，默认）：改图进入 Staging、素材进入聊天侧提案，须用户确认；确认后不自动保存测试流，须人手点保存。
 */
const AUTOPILOT_ENABLED_KEY = 'qualitest.aiDesign.autopilotEnabled';

/** 从 localStorage 读布尔偏好；未设置或解析失败返回 null */
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

/** 把布尔偏好写入 localStorage（值为 '1' / '0'） */
function writeBoolFlag(key: string, enabled: boolean): void {
  try {
    localStorage.setItem(key, enabled ? '1' : '0');
  } catch {
    /* ignore */
  }
}

/**
 * 是否开启测试流 AI 全自动。
 * true：改图隐式落盘、素材直写、可 run_test_flow。
 * 未设置或 false：半自动（Staging / 素材人审，确认后人手保存）。
 */
export function isAutopilotEnabled(): boolean {
  return readBoolFlag(AUTOPILOT_ENABLED_KEY) === true;
}

/** 写入全自动开关；true=全自动，false=半自动 */
export function setAutopilotEnabled(enabled: boolean): void {
  writeBoolFlag(AUTOPILOT_ENABLED_KEY, enabled);
}
