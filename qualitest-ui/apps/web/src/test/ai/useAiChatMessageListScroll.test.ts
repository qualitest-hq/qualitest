/**
 * 测 useAiChatMessageListScroll：贴底距离判定、非 force / force 滚底、pause、prepend 补偿。
 * 边界：jsdom；手动定义 scrollHeight/clientHeight；不测 MutationObserver。
 * 单跑：pnpm test useAiChatMessageListScroll
 * @vitest-environment jsdom
 */
import { describe, expect, it } from 'vitest';
import { effectScope, ref } from 'vue';

import {
  distanceFromListBottom,
  isNearStickBottom,
  useAiChatMessageListScroll,
} from '@/composables/ai/useAiChatMessageListScroll';
import { AI_CHAT_STICK_BOTTOM_PX } from '@/utils/ai/aiChatMessagePage';

/** 构造可控制 scrollHeight / clientHeight / scrollTop 的假滚动容器 */
function createScrollEl(opts: {
  scrollHeight: number;
  clientHeight: number;
  scrollTop?: number;
}): HTMLElement {
  const el = document.createElement('div');
  Object.defineProperty(el, 'scrollHeight', {
    configurable: true,
    get: () => opts.scrollHeight,
  });
  Object.defineProperty(el, 'clientHeight', {
    configurable: true,
    get: () => opts.clientHeight,
  });
  el.scrollTop = opts.scrollTop ?? 0;
  return el;
}

describe('distanceFromListBottom / isNearStickBottom', () => {
  it('距底小于阈值视为贴底', () => {
    // 前提：scrollHeight=500，clientHeight=200，scrollTop=280 → 距底 20
    // 期望：距离 20；小于默认阈值时 isNearStickBottom 为 true
    const el = { scrollHeight: 500, clientHeight: 200, scrollTop: 280 };
    expect(distanceFromListBottom(el)).toBe(20);
    expect(isNearStickBottom(el)).toBe(true);
    expect(AI_CHAT_STICK_BOTTOM_PX).toBe(48);
  });

  it('距底超过阈值不贴底', () => {
    // 前提：距底 100
    // 期望：isNearStickBottom 为 false
    const el = { scrollHeight: 500, clientHeight: 200, scrollTop: 200 };
    expect(distanceFromListBottom(el)).toBe(100);
    expect(isNearStickBottom(el)).toBe(false);
  });
});

describe('useAiChatMessageListScroll', () => {
  it('上滑后非 force 不改 scrollTop；force 强制贴底', async () => {
    // 前提：列表不在底部，stick 被 onListScroll 置为 false
    // 期望：scrollToBottom() 不滚动；scrollToBottom(true) 滚到底并恢复 stick
    const el = createScrollEl({ scrollHeight: 1000, clientHeight: 200, scrollTop: 100 });
    const listRef = ref<HTMLElement | null>(el);
    const scope = effectScope();
    const api = scope.run(() => useAiChatMessageListScroll(listRef))!;

    api.onListScroll();
    expect(api.stickToBottom.value).toBe(false);

    await api.scrollToBottom();
    expect(el.scrollTop).toBe(100);

    await api.scrollToBottom(true);
    expect(el.scrollTop).toBe(1000);
    expect(api.stickToBottom.value).toBe(true);

    scope.stop();
  });

  it('贴底时非 force 可滚到底', async () => {
    // 前提：已在底部附近，stick=true；内容变高后 scrollHeight 更新
    // 期望：scrollToBottom() 把 scrollTop 拉到新高度
    let height = 400;
    const el = document.createElement('div');
    Object.defineProperty(el, 'scrollHeight', {
      configurable: true,
      get: () => height,
    });
    Object.defineProperty(el, 'clientHeight', {
      configurable: true,
      get: () => 200,
    });
    el.scrollTop = 200;

    const listRef = ref<HTMLElement | null>(el);
    const scope = effectScope();
    const api = scope.run(() => useAiChatMessageListScroll(listRef))!;

    api.onListScroll();
    expect(api.stickToBottom.value).toBe(true);

    height = 800;
    await api.scrollToBottom();
    expect(el.scrollTop).toBe(800);

    scope.stop();
  });

  it('pause 期间非 force 不滚动；resume 后可再贴底', async () => {
    // 前提：pauseAutoStick 后 stick=false
    // 期望：非 force 无效；resume 且 force 后可贴底
    const el = createScrollEl({ scrollHeight: 500, clientHeight: 200, scrollTop: 0 });
    const listRef = ref<HTMLElement | null>(el);
    const scope = effectScope();
    const api = scope.run(() => useAiChatMessageListScroll(listRef))!;

    api.pauseAutoStick();
    expect(api.stickToBottom.value).toBe(false);
    expect(api.autoStickPaused.value).toBe(true);

    await api.scrollToBottom();
    expect(el.scrollTop).toBe(0);

    api.resumeAutoStick();
    await api.scrollToBottom(true);
    expect(el.scrollTop).toBe(500);
    expect(api.autoStickPaused.value).toBe(false);

    scope.stop();
  });

  it('preserveScrollAfterPrepend 补偿 scrollTop 且不贴底', async () => {
    // 前提：pause 后调用 preserve；在 nextTick 前把 scrollHeight 从 500 增到 700
    // 期望：scrollTop 从 50 变为 250，stick=false，pause 解除
    let height = 500;
    const el = document.createElement('div');
    Object.defineProperty(el, 'scrollHeight', {
      configurable: true,
      get: () => height,
    });
    Object.defineProperty(el, 'clientHeight', {
      configurable: true,
      get: () => 200,
    });
    el.scrollTop = 50;

    const listRef = ref<HTMLElement | null>(el);
    const scope = effectScope();
    const api = scope.run(() => useAiChatMessageListScroll(listRef))!;

    api.pauseAutoStick();
    const done = api.preserveScrollAfterPrepend();
    height = 700;
    await done;

    expect(el.scrollTop).toBe(250);
    expect(api.stickToBottom.value).toBe(false);
    expect(api.autoStickPaused.value).toBe(false);

    scope.stop();
  });
});

describe('aiChatMessagePage stick 常量', () => {
  it('贴底阈值大于 0', () => {
    // 前提：读取 AI_CHAT_STICK_BOTTOM_PX
    // 期望：为正数
    expect(AI_CHAT_STICK_BOTTOM_PX).toBeGreaterThan(0);
  });
});
