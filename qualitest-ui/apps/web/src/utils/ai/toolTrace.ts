/**
 * 工具调用轨迹的类型与解析，以及中断时助手气泡默认文案。
 */

/** 用户取消或连接中断后，助手气泡在无正文时使用的默认说明 */
export const AI_INTERRUPTED_MESSAGE = '本轮已中断';

/** 轨迹中单次工具调用 */
export interface AiToolTraceCallView {
  /** 本轮内从 1 起的序号 */
  i: number;
  /** 工具名 */
  name: string;
  /** 是否执行成功 */
  ok: boolean;
  /** 耗时毫秒 */
  ms?: number;
  /** 脱敏截断后的入参 */
  args?: Record<string, unknown> | unknown;
  /** 脱敏截断后的返回 */
  result?: Record<string, unknown> | unknown;
}

/** 一轮 Agent 结束后的完整工具轨迹 */
export interface AiToolTraceView {
  /** 实际消耗的工具轮数 */
  stepsUsed?: number;
  /** 本轮允许的最大工具轮数 */
  maxSteps?: number;
  /** 调用条数是否因上限被截断 */
  truncated?: boolean;
  /** 按执行顺序排列的调用列表 */
  calls?: AiToolTraceCallView[];
}

/**
 * 从助手消息元数据中解析 toolTrace。
 * meta 无该字段、非对象、或无法识别时返回 undefined。
 * 缺少 name 的 call 项会被跳过。
 */
export function parseToolTraceFromMeta(meta: Record<string, unknown>): AiToolTraceView | undefined {
  const raw = meta.toolTrace;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined;
  }
  const obj = raw as Record<string, unknown>;
  const callsRaw = obj.calls;
  const calls: AiToolTraceCallView[] = [];
  if (Array.isArray(callsRaw)) {
    for (const item of callsRaw) {
      if (!item || typeof item !== 'object') continue;
      const row = item as Record<string, unknown>;
      const name = typeof row.name === 'string' ? row.name : '';
      if (!name) continue;
      const i = typeof row.i === 'number' ? row.i : calls.length + 1;
      calls.push({
        i,
        name,
        ok: row.ok !== false,
        ms: typeof row.ms === 'number' ? row.ms : undefined,
        args: row.args,
        result: row.result,
      });
    }
  }
  return {
    stepsUsed: typeof obj.stepsUsed === 'number' ? obj.stepsUsed : undefined,
    maxSteps: typeof obj.maxSteps === 'number' ? obj.maxSteps : undefined,
    truncated: obj.truncated === true,
    calls,
  };
}
