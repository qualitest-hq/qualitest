/**
 * AI 会话消息列表滚动：智能贴底。
 *
 * 行为：
 * - stickToBottom 为 true 时，列表内容变高（流式打字、思考块等）会自动滚到最底
 * - 用户上滑离开底部后 stickToBottom 变 false，停止自动跟随，视口停在当前位置
 * - 用户再滚回距底小于阈值内时，重新设为 true，恢复自动跟随
 * - scrollToBottom(true) 强制滚到底并打开跟随（发送消息、点「回到最新」）
 * - 向上加载更早消息时先 pause，防止 DOM 变化把视口拽回底部
 */
import { nextTick, ref, watch, type Ref } from 'vue';

import { AI_CHAT_STICK_BOTTOM_PX } from '@/utils/ai/aiChatMessagePage';

/**
 * 计算滚动容器距底部还有多少像素。
 * scrollHeight - scrollTop - clientHeight。
 */
export function distanceFromListBottom(
  el: Pick<HTMLElement, 'scrollHeight' | 'scrollTop' | 'clientHeight'>,
): number {
  return el.scrollHeight - el.scrollTop - el.clientHeight;
}

/**
 * 当前是否落在「贴底跟随」区间内。
 * 距底小于 thresholdPx（默认 AI_CHAT_STICK_BOTTOM_PX）时返回 true。
 */
export function isNearStickBottom(
  el: Pick<HTMLElement, 'scrollHeight' | 'scrollTop' | 'clientHeight'>,
  thresholdPx = AI_CHAT_STICK_BOTTOM_PX,
): boolean {
  return distanceFromListBottom(el) < thresholdPx;
}

/**
 * @param listRef 消息列表可滚动 DOM（通常来自壳层 getMessageScrollElement）
 */
export function useAiChatMessageListScroll(listRef: Ref<HTMLElement | null>) {
  /** true：内容增高时自动滚到底；false：用户已离开底部，不再跟滚 */
  const stickToBottom = ref(true);
  /** true：暂停自动贴底（加载更早消息期间），非 force 的滚底也会被拦住 */
  const autoStickPaused = ref(false);

  /** 暂停自动贴底，并关闭 stick（加载更早消息前调用） */
  function pauseAutoStick() {
    autoStickPaused.value = true;
    stickToBottom.value = false;
  }

  /** 结束暂停；不改变 stickToBottom，由调用方决定是否再贴底 */
  function resumeAutoStick() {
    autoStickPaused.value = false;
  }

  /** 立刻把 scrollTop 拉到最底，并打开 stickToBottom */
  function applyScrollToBottom() {
    const el = listRef.value;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
    stickToBottom.value = true;
  }

  /**
   * DOM 变更后若仍应贴底，则在下一帧滚到底。
   * 用 rAF 合并同一帧内多次 MutationObserver 回调。
   */
  function scheduleStickIfNeeded() {
    if (autoStickPaused.value || !stickToBottom.value) return;
    requestAnimationFrame(() => {
      if (autoStickPaused.value || !stickToBottom.value) return;
      applyScrollToBottom();
    });
  }

  /**
   * 滚到列表最底。
   * force=false：仅 stick 开启且未 pause 时滚动。
   * force=true：无视 stick/pause 状态，强制贴底并重新打开跟随。
   */
  async function scrollToBottom(force = false) {
    if (!force && (autoStickPaused.value || !stickToBottom.value)) return;
    await nextTick();
    applyScrollToBottom();
  }

  /**
   * 列表 scroll 事件：按距底距离更新 stickToBottom。
   * pause 期间不改 stick，避免加载更早消息时抖动。
   */
  function onListScroll() {
    const el = listRef.value;
    if (!el || autoStickPaused.value) return;
    stickToBottom.value = isNearStickBottom(el);
  }

  /**
   * 列表头部插入更早消息后，按增高量补偿 scrollTop，避免视口跳动。
   * 调用前须已 pauseAutoStick；结束后解除 pause，并保持 stick=false。
   * 调用时先记下当前高度，nextTick（DOM 已增高）后再：新高度 - 旧高度 + 旧 scrollTop。
   */
  async function preserveScrollAfterPrepend() {
    const el = listRef.value;
    if (!el) {
      resumeAutoStick();
      return;
    }
    const prevHeight = el.scrollHeight;
    const prevTop = el.scrollTop;
    await nextTick();
    el.scrollTop = el.scrollHeight - prevHeight + prevTop;
    stickToBottom.value = false;
    resumeAutoStick();
  }

  // 列表 DOM 子树变化（流式文本、节点增删）时尝试贴底跟随
  watch(
    listRef,
    (el, _prev, onCleanup) => {
      if (!el || typeof MutationObserver === 'undefined') return;
      const mo = new MutationObserver(() => scheduleStickIfNeeded());
      mo.observe(el, { childList: true, subtree: true, characterData: true });
      onCleanup(() => mo.disconnect());
    },
    { flush: 'post' },
  );

  return {
    stickToBottom,
    autoStickPaused,
    pauseAutoStick,
    resumeAutoStick,
    scrollToBottom,
    onListScroll,
    preserveScrollAfterPrepend,
  };
}
