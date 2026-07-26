/**
 * 请求体 body.mode 取值判断。
 * 标准取值为 x-www-form-urlencoded；流程节点内嵌配置可能使用简写 urlencoded。
 */

/** body.mode 标准值 */
export const URLENCODED_BODY_MODE = 'x-www-form-urlencoded';

/** body.mode 简写值（流程节点快照中可能出现） */
export const URLENCODED_BODY_MODE_SHORT = 'urlencoded';

/** 判断 mode 是否表示 urlencoded 表单请求体 */
export function isUrlencodedBodyMode(mode: unknown): boolean {
  const m = String(mode ?? '');
  return m === URLENCODED_BODY_MODE || m === URLENCODED_BODY_MODE_SHORT;
}
