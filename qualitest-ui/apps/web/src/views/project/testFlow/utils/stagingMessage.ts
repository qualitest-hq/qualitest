/**
 * AI 设计会话消息与 Staging 展示辅助。
 */
import type { AiDesignMessageView } from '../types/aiDesignTypes';

export function shouldShowStagingSummary(msg: AiDesignMessageView): boolean {
  return msg.role === 'assistant' && !!msg.patch && !msg.explainOnly;
}

/**
 * 本轮未调用 submit：明示无 Staging，避免用户以为已改图。
 * @param streamingMessageId 流式中的消息 id；传入时对该条不展示，避免闪一下
 */
export function shouldShowExplainOnlyHint(
  msg: AiDesignMessageView,
  streamingMessageId?: string | null,
): boolean {
  if (msg.role !== 'assistant' || msg.explainOnly !== true) return false;
  if (streamingMessageId && msg.id === streamingMessageId) return false;
  return true;
}
