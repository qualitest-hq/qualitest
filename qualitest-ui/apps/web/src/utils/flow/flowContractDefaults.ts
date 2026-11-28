/**
 * 流图跨节点类型的产品默认值（前端侧）。
 * 用于新建节点、面板回退；与后端落库补写使用同一套常量含义。
 * 本文件只含共用项；HTTP 完整 fallbackOut、子流业务兜底各有独立模块。
 */

/** 返回节点 data.from 为空时的默认路径 */
export const DEFAULT_FROM = 'flow.out'

/** 入口节点未配置 maxSteps 时的默认步数上限 */
export const DEFAULT_MAX_STEPS = 200

/** maxSteps 允许的最大值 */
export const MAX_STEPS_CEILING = 1000

/** 跑完未命中返回节点时，兜底业务对象的 code */
export const NO_RETURN_CODE = 'NO_RETURN'

/** 跑完未命中返回节点时，兜底业务对象的 message */
export const NO_RETURN_MESSAGE = '未执行到返回节点'

/** 零次返回业务对象，供 HTTP body / 子流业务兜底共用 */
export function defaultNoReturnBody(): { code: string; message: string } {
  return { code: NO_RETURN_CODE, message: NO_RETURN_MESSAGE }
}
