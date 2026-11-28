/**
 * HTTP 入口（http-entry）专用默认值。
 * 构造完整 fallbackOut：status + headers + body。
 */
import { defaultNoReturnBody } from './flowContractDefaults'

/** 默认兜底 HTTP 状态码 */
export const DEFAULT_FALLBACK_HTTP_STATUS = 500

/** 默认兜底 Content-Type */
export const DEFAULT_FALLBACK_CONTENT_TYPE = 'application/json;charset=UTF-8'

/** 新建 HTTP 入口或面板回退时的完整 fallbackOut */
export function defaultHttpFallbackOut() {
  return {
    status: DEFAULT_FALLBACK_HTTP_STATUS,
    headers: { 'Content-Type': DEFAULT_FALLBACK_CONTENT_TYPE },
    body: defaultNoReturnBody(),
  }
}
