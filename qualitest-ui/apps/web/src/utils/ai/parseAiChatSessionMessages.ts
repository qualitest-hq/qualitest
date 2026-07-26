import type { AiChatMessageItem } from '@/api/ai/chat';

export interface AiChatSessionParseResult<TMessage extends { id: string }> {
  messages: TMessage[];
  messageAcceptedMap: Record<string, Set<string>>;
}

/**
 * 将服务端会话消息列表解析为面板视图，并收集 patch 初始勾选项。
 */
export function parseAiChatSessionMessages<TMessage extends { id: string }>(
  raw: AiChatMessageItem[],
  handlers: {
    parseUser: (msg: AiChatMessageItem) => TMessage;
    parseAssistant: (msg: AiChatMessageItem) => TMessage;
    buildAcceptedIds?: (view: TMessage) => Set<string> | undefined;
  },
): AiChatSessionParseResult<TMessage> {
  const messages: TMessage[] = [];
  const messageAcceptedMap: Record<string, Set<string>> = {};

  for (const msg of raw) {
    if (msg.messageRole === 'user') {
      messages.push(handlers.parseUser(msg));
      continue;
    }
    if (msg.messageRole !== 'assistant') continue;

    const view = handlers.parseAssistant(msg);
    messages.push(view);
    const accepted = handlers.buildAcceptedIds?.(view);
    if (accepted) {
      messageAcceptedMap[view.id] = accepted;
    }
  }

  return { messages, messageAcceptedMap };
}
