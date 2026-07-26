import { getAiChatMessageMeta, type AiChatMessageItem } from '@/api/ai/chat';

const metaCache = new Map<string, AiChatMessageItem>();
const inflight = new Map<string, Promise<AiChatMessageItem>>();

/** 清空 Lazy Patch meta 缓存（切换会话 / 重置面板时调用） */
export function clearMessageMetaCache() {
  metaCache.clear();
  inflight.clear();
}

/** 按需拉取单条消息完整 meta（含 patchJson），同 id 并发合并为一次请求 */
export async function fetchMessageMetaCached(messageId: string): Promise<AiChatMessageItem> {
  const id = String(messageId).trim();
  if (!id) {
    throw new Error('缺少 messageId');
  }
  const cached = metaCache.get(id);
  if (cached) return cached;

  const pending = inflight.get(id);
  if (pending) return pending;

  const task = getAiChatMessageMeta(id).then((res) => {
    const item = res.data as AiChatMessageItem;
    metaCache.set(id, item);
    inflight.delete(id);
    return item;
  }).catch((err) => {
    inflight.delete(id);
    throw err;
  });

  inflight.set(id, task);
  return task;
}
