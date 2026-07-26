/**
 * AI 设计会话消息与 Staging 展示辅助。
 */
import type { AiDesignMessageView } from '../types/aiDesignTypes';

export function shouldShowStagingSummary(msg: AiDesignMessageView): boolean {
  return msg.role === 'assistant' && !!msg.patch && !msg.explainOnly;
}
