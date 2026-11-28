/**
 * 子流入口（subflow-entry）专用默认值。
 * 兜底是业务对象；引擎收束时再包成 { body: … } 挂到父流。
 */
import { defaultNoReturnBody } from './flowContractDefaults'

/** 新建子流入口或面板回退时的业务兜底对象 */
export function defaultSubflowFallbackOut() {
  return defaultNoReturnBody()
}
