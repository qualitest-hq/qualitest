/**
 * AI 助手面板消息列表行为。
 *
 * 1. 智能贴底
 *    - 开始生成（designing=true）时强制滚到底并打开跟随
 *    - 消息条数变化时，若仍贴底则跟随滚底
 *    - 列表 DOM 增高时由滚动 composable 内 MutationObserver 跟滚
 *    - stickToBottom 供壳层「回到最新」按钮显隐
 * 2. 触顶加载更早消息：先 pause 贴底，加载成功后补偿 scrollTop，失败则 resume
 * 3. 带 data-patch-pending 的消息行进入视口时，按需拉取 patch 详情
 */
import { computed, nextTick, onMounted, onUnmounted, watch, type Ref } from 'vue';

import AiChatShell from '@/components/ai/AiChatShell.vue';
import { useAiChatMessageListScroll } from '@/composables/ai/useAiChatMessageListScroll';

export interface UseAiChatPanelListOptions {
  /** 当前渲染中的消息（含流式占位），DOM 变化后重绑 patch 观察器 */
  displayMessages: Ref<readonly unknown[]>;
  /** 消息条数；变化时在仍贴底时非强制滚底 */
  messageCount: Ref<number>;
  /** 是否正在生成；变为 true 时强制贴底并重新跟随 */
  designing: Ref<boolean>;
  /** 加载更早一页历史消息；返回是否加载成功 */
  loadOlderMessages: () => Promise<boolean>;
  /** 某条 assistant 消息进入视口且 patch 未加载时拉取详情 */
  ensureMessagePatchLoaded: (messageId: string) => Promise<boolean>;
  /** 壳层实例，用于取消息列表滚动 DOM */
  shellRef: Ref<InstanceType<typeof AiChatShell> | null>;
}

export function useAiChatPanelList(options: UseAiChatPanelListOptions) {
  /** 消息列表可滚动 DOM；壳层未挂载时为 null */
  const listRef = computed(() => options.shellRef.value?.getMessageScrollElement() ?? null);

  const {
    scrollToBottom,
    onListScroll,
    preserveScrollAfterPrepend,
    pauseAutoStick,
    resumeAutoStick,
    stickToBottom,
  } = useAiChatMessageListScroll(listRef);

  let patchObserver: IntersectionObserver | null = null;

  /**
   * 观察列表内 data-patch-pending="1" 的行。
   * 进入视口后调用 ensureMessagePatchLoaded 拉取该消息的 patch。
   */
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

  /**
   * 用户滚到列表顶部触发：加载更早一页。
   * 成功则补偿视口高度并重绑 patch 观察；失败则解除 pause。
   */
  async function onLoadOlderMessages() {
    pauseAutoStick();
    const ok = await options.loadOlderMessages();
    if (ok) {
      await preserveScrollAfterPrepend();
      nextTick(() => bindPatchObserver());
      return;
    }
    resumeAutoStick();
  }

  // 条数变化（新消息、流式占位插入等）：仅在仍贴底时跟随
  watch(options.messageCount, () => {
    void scrollToBottom();
  });

  // 用户发送 / 开始生成：强制贴底，便于看到最新输出
  watch(options.designing, (active) => {
    if (active) void scrollToBottom(true);
  });

  // 壳层实例变化后重绑 patch 观察
  watch(() => options.shellRef.value, () => nextTick(() => bindPatchObserver()));

  // 消息内容变化后重扫 pending 行
  watch(options.displayMessages, () => nextTick(() => bindPatchObserver()), { deep: true });

  onMounted(() => nextTick(() => bindPatchObserver()));
  onUnmounted(() => patchObserver?.disconnect());

  return {
    listRef,
    /** 是否处于贴底跟随；false 时壳层应显示「回到最新」 */
    stickToBottom,
    onListScroll,
    onLoadOlderMessages,
    scrollToBottom,
    bindPatchObserver,
  };
}
