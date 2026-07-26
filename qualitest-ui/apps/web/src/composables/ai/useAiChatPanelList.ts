/**
 * AI 助手面板消息列表行为。
 *
 * 负责三件事：
 * 1. 新消息到达或流式输出时自动滚到底部
 * 2. 用户向上滚动到顶部时加载更早消息，并保持视口位置不跳动
 * 3. 用 IntersectionObserver 监听带 data-patch-pending 的消息行，进入视口后触发 patch 懒加载
 */
import { computed, nextTick, onMounted, onUnmounted, watch, type Ref } from 'vue';

import AiChatShell from '@/components/ai/AiChatShell.vue';
import { useAiChatMessageListScroll } from '@/composables/ai/useAiChatMessageListScroll';

export interface UseAiChatPanelListOptions {
  /** 当前渲染中的消息（含流式占位），用于观察 DOM 变化后重新绑定 Observer */
  displayMessages: Ref<readonly unknown[]>;
  /** 消息条数，变化时触发贴底滚动 */
  messageCount: Ref<number>;
  /** 加载更早一页历史消息 */
  loadOlderMessages: () => Promise<boolean>;
  /** 按需拉取某条 assistant 消息的 patch 详情 */
  ensureMessagePatchLoaded: (messageId: string) => Promise<boolean>;
  /** 已挂载的 AiChatShell 实例，用来拿到内部消息列表的滚动容器 */
  shellRef: Ref<InstanceType<typeof AiChatShell> | null>;
}

export function useAiChatPanelList(options: UseAiChatPanelListOptions) {
  /** 消息列表的可滚动 DOM 节点 */
  const listRef = computed(() => options.shellRef.value?.getMessageScrollElement() ?? null);

  const {
    scrollToBottom,
    onListScroll,
    preserveScrollAfterPrepend,
  } = useAiChatMessageListScroll(listRef);

  let patchObserver: IntersectionObserver | null = null;

  /** 扫描列表中带 data-patch-pending 的行，进入视口时拉取 patch */
  function bindPatchObserver() {
    patchObserver?.disconnect();
    const root = listRef.value;
    if (!root) return;

    patchObserver = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const el = entry.target as HTMLElement;
          const id = el.dataset.messageId;
          if (id && el.dataset.patchPending === '1') {
            void options.ensureMessagePatchLoaded(id);
          }
        }
      },
      { root, threshold: 0.1 },
    );

    root.querySelectorAll('[data-patch-pending="1"]').forEach((el: Element) => {
      patchObserver!.observe(el);
    });
  }

  /** 向上分页加载完成后恢复滚动位置，并重新绑定 patch 观察器 */
  async function onLoadOlderMessages() {
    const ok = await options.loadOlderMessages();
    if (ok) {
      await preserveScrollAfterPrepend();
      nextTick(() => bindPatchObserver());
    }
  }

  watch(options.messageCount, () => {
    void scrollToBottom();
  });

  // Shell 挂载或切换后，重新绑定观察器
  watch(() => options.shellRef.value, () => nextTick(() => bindPatchObserver()));

  watch(options.displayMessages, () => nextTick(() => bindPatchObserver()), { deep: true });

  onMounted(() => nextTick(() => bindPatchObserver()));
  onUnmounted(() => patchObserver?.disconnect());

  return {
    listRef,
    onListScroll,
    onLoadOlderMessages,
    scrollToBottom,
    bindPatchObserver,
  };
}
