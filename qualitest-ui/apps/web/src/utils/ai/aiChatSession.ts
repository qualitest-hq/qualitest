import type { AiChatSessionItem } from '@/api/ai/chat';

/** 未落库的本地新对话占位 ID */
export const DRAFT_SESSION_ID = '__draft__';

/** 是否为已持久化、可同步到后端的会话 ID */
export function isPersistedSessionId(sessionId: string | null | undefined): boolean {
  return Boolean(sessionId && sessionId !== DRAFT_SESSION_ID);
}

/** 生成客户端临时消息 ID */
export function createClientMessageId(): string {
  return `client-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

/** 规范化会话列表项中的 ID 字段为字符串 */
export function normalizeChatSessionList(rows: AiChatSessionItem[] | undefined): AiChatSessionItem[] {
  return (rows ?? []).map((s) => ({
    ...s,
    aiChatSessionId: String(s.aiChatSessionId),
    currentModelId: s.currentModelId != null ? String(s.currentModelId) : undefined,
  }));
}

/** 解析服务端会话思考开关：0 关、1 开，其余视为未设置 */
export function parseSessionThinkingFlag(value: number | null | undefined): number | null {
  return value === 0 || value === 1 ? value : null;
}
