/**
 * AI 设计会话消息与 Staging 展示辅助。
 */

import type { AiDesignMessageView } from '../types/aiDesignTypes';

/** 助手消息带有可灌 Staging 的 patch，且非纯答疑时展示 Staging 摘要条 */
export function shouldShowStagingSummary(msg: AiDesignMessageView): boolean {
  return msg.role === 'assistant' && !!msg.patch && !msg.explainOnly;
}
