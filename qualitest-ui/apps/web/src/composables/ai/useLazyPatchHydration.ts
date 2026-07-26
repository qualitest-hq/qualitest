import { type Ref } from 'vue';

import type { AiChatMessageItem } from '@/api/ai/chat';
import { fetchMessageMetaCached } from '@/utils/ai/lazyMessageMeta';
import {
  resolveActivePatchMessageId,
  type AiPatchMessageView,
} from '@/utils/ai/aiPatchMessageSelectors';

export interface UseLazyPatchHydrationOptions<TMessage extends AiPatchMessageView> {
  messages: Ref<TMessage[]>;
  /** 从 meta 接口响应还原 assistant 视图 */
  hydrateAssistant: (raw: AiChatMessageItem) => TMessage;
  /** patch 懒加载完成后的回调 */
  onHydrated?: (messageId: string) => void;
  /** @deprecated 仅 testProject 旧 Diff 链路使用 */
  messageAcceptedMap?: Ref<Record<string, Set<string>>>;
  /** @deprecated 仅 testProject 旧 Diff 链路使用 */
  buildAcceptedIds?: (hydrated: TMessage) => Set<string>;
  /** @deprecated 仅 testProject 旧 Diff 链路使用 */
  preserveLocalFields?: (current: TMessage) => Partial<TMessage>;
}

/**
 * Lazy Patch：按需拉取 patchJson 并合并到消息列表。
 */
export function useLazyPatchHydration<TMessage extends AiPatchMessageView>(
  options: UseLazyPatchHydrationOptions<TMessage>,
) {
  const {
    messages,
    hydrateAssistant,
    onHydrated,
    messageAcceptedMap,
    buildAcceptedIds,
    preserveLocalFields,
  } = options;

  const patchLoadInflight = new Set<string>();
  const patchLoadPromises = new Map<string, Promise<boolean>>();

  async function loadMessagePatch(messageId: string): Promise<boolean> {
    const idx = messages.value.findIndex((m) => m.id === messageId);
    if (idx < 0) return false;

    const msg = messages.value[idx];
    if (msg.role !== 'assistant' || msg.explainOnly || msg.patch || !msg.patchPending) {
      return Boolean(msg.patch);
    }

    patchLoadInflight.add(messageId);

    const loading = [...messages.value];
    loading[idx] = { ...msg, patchLoading: true };
    messages.value = loading;

    try {
      const raw = await fetchMessageMetaCached(messageId);
      const hydrated = hydrateAssistant(raw);
      const currentIdx = messages.value.findIndex((m) => m.id === messageId);
      if (currentIdx < 0) return false;

      const current = messages.value[currentIdx];
      const next = [...messages.value];
      next[currentIdx] = {
        ...current,
        ...hydrated,
        ...preserveLocalFields?.(current),
        patchPending: false,
        patchLoading: false,
      };
      messages.value = next;

      if (
        messageAcceptedMap &&
        hydrated.patch &&
        !hydrated.explainOnly &&
        !messageAcceptedMap.value[messageId]
      ) {
        messageAcceptedMap.value = {
          ...messageAcceptedMap.value,
          [messageId]: buildAcceptedIds?.(hydrated) ?? new Set<string>(),
        };
      }

      onHydrated?.(messageId);
      return Boolean(hydrated.patch);
    } catch {
      const errIdx = messages.value.findIndex((m) => m.id === messageId);
      if (errIdx >= 0) {
        const next = [...messages.value];
        next[errIdx] = { ...messages.value[errIdx], patchLoading: false };
        messages.value = next;
      }
      return false;
    } finally {
      patchLoadInflight.delete(messageId);
    }
  }

  async function ensureMessagePatchLoaded(messageId: string): Promise<boolean> {
    const idx = messages.value.findIndex((m) => m.id === messageId);
    if (idx < 0) return false;

    const msg = messages.value[idx];
    if (msg.role !== 'assistant' || msg.explainOnly || msg.patch || !msg.patchPending) {
      return Boolean(msg.patch);
    }

    const inflight = patchLoadPromises.get(messageId);
    if (inflight) {
      return inflight;
    }

    const promise = loadMessagePatch(messageId).finally(() => {
      patchLoadPromises.delete(messageId);
    });
    patchLoadPromises.set(messageId, promise);
    return promise;
  }

  async function eagerLoadActivePatch(loadedMessages: TMessage[]) {
    const activeId = resolveActivePatchMessageId(loadedMessages);
    if (activeId) {
      await ensureMessagePatchLoaded(activeId);
    }
  }

  return { ensureMessagePatchLoaded, eagerLoadActivePatch };
}
