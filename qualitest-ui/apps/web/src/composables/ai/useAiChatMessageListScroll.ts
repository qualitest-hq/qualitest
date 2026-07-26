import { nextTick, ref, type Ref } from 'vue';

/**
 * AI 会话消息列表滚动：贴底、向上加载更早消息时保持视口位置。
 */
export function useAiChatMessageListScroll(listRef: Ref<HTMLElement | null>) {
  const stickToBottom = ref(true);

  async function scrollToBottom(force = false) {
    if (!force && !stickToBottom.value) return;
    await nextTick();
    const el = listRef.value;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
    stickToBottom.value = true;
  }

  function onListScroll() {
    const el = listRef.value;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    stickToBottom.value = distanceFromBottom < 48;
  }

  /** 在列表头部 prepend 更早消息后，补偿 scrollTop 避免跳动 */
  async function preserveScrollAfterPrepend() {
    const el = listRef.value;
    if (!el) return;
    const prevHeight = el.scrollHeight;
    const prevTop = el.scrollTop;
    await nextTick();
    el.scrollTop = el.scrollHeight - prevHeight + prevTop;
    stickToBottom.value = false;
  }

  return {
    stickToBottom,
    scrollToBottom,
    onListScroll,
    preserveScrollAfterPrepend,
  };
}
