/**
 * 用户取消流式设计后的半成品恢复：短暂等待落盘 → 重拉会话 → 必要时本地兜底气泡。
 */
import type { Ref } from 'vue';

import { createClientMessageId } from '@/utils/ai/aiChatSession';
import { AI_INTERRUPTED_MESSAGE } from '@/utils/ai/toolTrace';

/** 可追加 interrupted 助手消息的最小消息形状 */
export interface InterruptedAssistantMessage {
  id: string;
  role: 'assistant';
  content: string;
  thinkingContent?: string;
  interrupted: true;
  explainOnly: true;
}

export interface RecoverInterruptedDesignOptions<TMessage extends { role: string }> {
  /** 取消前本地已收到的正文 */
  streamText: Ref<string>;
  /** 取消前本地已收到的思考 */
  streamThinking: Ref<string>;
  /** 当前会话消息列表（可写） */
  messages: Ref<TMessage[]>;
  /** 强制重拉当前会话；成功返回 true */
  reloadActiveSession: () => Promise<boolean>;
}

/**
 * 取消流式请求后的收尾：先短暂等待服务端落盘，再强制重拉当前会话。
 * 若已有助手消息则直接采用服务端半成品；否则用本地已收到的正文/思考兜底，并标 interrupted。
 */
export async function recoverInterruptedDesign<TMessage extends { role: string }>(
  options: RecoverInterruptedDesignOptions<TMessage>,
): Promise<void> {
  const localText = options.streamText.value.trim();
  const localThinking = options.streamThinking.value.trim();
  // 给服务端协作停止与落盘留一点时间
  await new Promise((resolve) => setTimeout(resolve, 500));
  const reloaded = await options.reloadActiveSession();
  if (reloaded) {
    const last = options.messages.value[options.messages.value.length - 1];
    if (last?.role === 'assistant') {
      return;
    }
  }
  const fallback = {
    id: createClientMessageId(),
    role: 'assistant' as const,
    content: localText || AI_INTERRUPTED_MESSAGE,
    thinkingContent: localThinking || undefined,
    interrupted: true as const,
    explainOnly: true as const,
  };
  options.messages.value = [
    ...options.messages.value,
    fallback as unknown as TMessage,
  ];
}
